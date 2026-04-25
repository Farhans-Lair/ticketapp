#!/bin/bash
# =============================================================
#  user_data.sh
#
#  EC2 first-boot bootstrap for the Spring Boot ticketapp.
#  Runs once when ASG launches a new instance.
#
#  FIX — root cause of 503 / EC2 not launching:
#    On first terraform apply, ECR has no image yet.
#    The old script had `set -e` which caused the entire
#    user_data to abort when `docker pull` failed (no image).
#    The instance then never passed the ALB /health check,
#    the ASG marked it unhealthy and terminated it → 503.
#
#    Fix: separate the one-time setup (always succeeds) from
#    the docker run (skipped gracefully if image not yet pushed).
#    On first push via CI/CD, GitHub Actions SSM deploy runs
#    docker pull + docker run on the live instance.
#
#  application.properties env var mapping:
#    server.port       → 8080
#    server.ssl.enabled → USE_HTTPS=false (ALB terminates TLS)
#    cookie.secure      → COOKIE_SECURE=true
#    spring.datasource.url → DB_HOST / DB_PORT / DB_NAME
#    spring.datasource.username → DB_USER
#    spring.datasource.password → DB_PASS
#    jwt.secret         → JWT_SECRET
#    spring.mail.*      → EMAIL_USER / EMAIL_PASS
#    aws.region         → AWS_REGION
#    aws.s3.bucket      → S3_BUCKET_NAME
#    razorpay.*         → RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET
#    frontend.url       → FRONTEND_URL
# =============================================================

# ── DO NOT use set -e globally ────────────────────────────────
# set -e would abort the entire script if docker pull fails
# (which it will on first apply before any image is pushed).
# We handle errors explicitly per-section instead.
set -uo pipefail
exec > /var/log/user-data.log 2>&1

echo "=========================================="
echo " ticketapp user_data.sh starting"
echo " $(date)"
echo "=========================================="

# ── 1. System update ──────────────────────────────────────────
echo "[1/7] System update..."
yum update -y || { echo "WARNING: yum update had errors, continuing"; true; }

# ── 2. Remove conflicting MariaDB libs ───────────────────────
echo "[2/7] Removing conflicting MariaDB packages..."
yum remove mariadb mariadb-libs -y || true

# ── 3. MySQL 8.0 client ───────────────────────────────────────
# Used for health checks and manual migration runs.
# Install is best-effort — not needed for app to run.
echo "[3/7] Installing MySQL 8.0 client..."
yum install https://dev.mysql.com/get/mysql80-community-release-el7-11.noarch.rpm -y || true
yum-config-manager --enable mysql80-community || true
yum install mysql-community-client -y || true

# ── 4. Docker ─────────────────────────────────────────────────
echo "[4/7] Installing and starting Docker..."
yum install -y docker
systemctl enable docker
systemctl start docker

# Wait for Docker daemon — required before any docker commands
WAITED=0
until docker info >/dev/null 2>&1; do
  if [ "$WAITED" -ge 60 ]; then
    echo "ERROR: Docker daemon did not start within 60s"
    exit 1
  fi
  sleep 5
  WAITED=$((WAITED + 5))
done
echo "Docker is ready."

usermod -aG docker ec2-user

# ── 5. App directory + .env file ──────────────────────────────
echo "[5/7] Creating app directory and .env file..."
APP_DIR=/home/ec2-user/ticketapp-backend
mkdir -p "$APP_DIR/logs"

# All values rendered by Terraform template_file from terraform.tfvars.
# Matches application.properties env var names exactly.
cat > "$APP_DIR/.env" <<ENVEOF
# ── Server ─────────────────────────────────────────────────
SERVER_PORT=8080
# HttpsConfig.java reads server.http.port — must be set even when USE_HTTPS=false
# (Spring property injection happens regardless of ConditionalOnProperty)
SERVER_HTTP_PORT=8080
USE_HTTPS=false
COOKIE_SECURE=true
FRONTEND_URL=https://${ALB_DNS}

# ── Database (spring.datasource.*) ─────────────────────────
DB_HOST=${DB_HOST}
DB_PORT=3306
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASS=${DB_PASS}

# ── JWT (jwt.secret) ────────────────────────────────────────
JWT_SECRET=${JWT_SECRET}

# ── Email (spring.mail.*) ───────────────────────────────────
EMAIL_USER=${EMAIL_USER}
EMAIL_PASS=${EMAIL_PASS}

# ── AWS S3 (aws.region / aws.s3.bucket) ────────────────────
AWS_REGION=${AWS_REGION}
S3_BUCKET_NAME=${S3_BUCKET_NAME}
# Blank → S3Config.java uses EC2 IAM instance role credentials
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# ── Razorpay ────────────────────────────────────────────────
RAZORPAY_KEY_ID=${RAZORPAY_KEY_ID}
RAZORPAY_KEY_SECRET=${RAZORPAY_KEY_SECRET}

# ── SSL (disabled — ALB handles TLS) ───────────────────────
SSL_KEYSTORE_TYPE=PKCS12
SSL_KEYSTORE_PATH=classpath:certs/keystore.p12
SSL_KEYSTORE_PASSWORD=changeit
SSL_KEY_ALIAS=1
ENVEOF

chown ec2-user:ec2-user "$APP_DIR/.env"
chmod 600 "$APP_DIR/.env"
echo ".env written to $APP_DIR/.env"

# ── 6. CloudWatch Agent ───────────────────────────────────────
echo "[6/7] Configuring CloudWatch agent..."
yum install -y amazon-cloudwatch-agent

cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<'CWEOF'
{
  "agent": { "run_as_user": "root" },
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/home/ec2-user/ticketapp-backend/logs/app.log",
            "log_group_name": "/ticketapp/backend",
            "log_stream_name": "{instance_id}/app",
            "timestamp_format": "%Y-%m-%dT%H:%M:%S",
            "multi_line_start_pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}"
          },
          {
            "file_path": "/home/ec2-user/ticketapp-backend/logs/error.log",
            "log_group_name": "/ticketapp/errors",
            "log_stream_name": "{instance_id}/errors",
            "timestamp_format": "%Y-%m-%dT%H:%M:%S",
            "multi_line_start_pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}"
          },
          {
            "file_path": "/var/log/user-data.log",
            "log_group_name": "/ticketapp/ec2-bootstrap",
            "log_stream_name": "{instance_id}/user-data",
            "timestamp_format": "%Y-%m-%dT%H:%M:%S"
          }
        ]
      }
    }
  },
  "metrics": {
    "namespace": "ticketapp/EC2",
    "metrics_collected": {
      "cpu":  { "measurement": ["cpu_usage_active"],  "metrics_collection_interval": 60 },
      "mem":  { "measurement": ["mem_used_percent"],  "metrics_collection_interval": 60 },
      "disk": { "measurement": ["disk_used_percent"], "resources": ["/"], "metrics_collection_interval": 60 }
    }
  }
}
CWEOF

/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 \
  -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json || true

systemctl enable amazon-cloudwatch-agent || true
echo "CloudWatch agent configured."

# ── 7. ECR Login + Pull + Run ────────────────────────────────
echo "[7/7] Attempting ECR login and container start..."

ECR_URI="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_URI="$ECR_URI/${PROJECT_NAME}-backend:latest"

# Login to ECR — uses the EC2 instance IAM role (AmazonEC2ContainerRegistryReadOnly)
if aws ecr get-login-password --region ${AWS_REGION} \
    | docker login --username AWS --password-stdin "$ECR_URI"; then
  echo "ECR login successful."

  # Attempt to pull the image — this WILL FAIL on first terraform apply
  # because no image has been pushed yet. That is expected.
  # CI/CD (GitHub Actions SSM) will push the image and restart the container.
  if docker pull "$IMAGE_URI"; then
    echo "Image pulled successfully. Starting container..."

    docker rm -f ticketapp-backend || true

    # server.port=8080 in application.properties → -p 8080:8080
    # USE_HTTPS=false → plain HTTP, ALB terminates TLS
    # /app/logs → mounted so CloudWatch agent can read app.log + error.log
    docker run -d \
      --name ticketapp-backend \
      --restart always \
      --env-file "$APP_DIR/.env" \
      -p 8080:8080 \
      -v "$APP_DIR/logs":/app/logs \
      --memory="900m" \
      --memory-swap="900m" \
      "$IMAGE_URI"

    # Wait for Spring Boot /health
    # HealthController.java → GET /health → {"status":"ok"} → HTTP 200
    # Cold start: JVM init + Hibernate DDL ≈ 30-60s
    echo "Waiting for Spring Boot to become healthy..."
    MAX_WAIT=180
    WAITED=0
    until curl -sf http://localhost:8080/health > /dev/null 2>&1; do
      if [ "$WAITED" -ge "$MAX_WAIT" ]; then
        echo "WARNING: Spring Boot did not pass /health within $${MAX_WAIT}s"
        echo "Container logs:"
        docker logs ticketapp-backend --tail 50 || true
        # Do NOT exit 1 here — instance should stay running for SSM access
        break
      fi
      sleep 5
      WAITED=$((WAITED + 5))
      echo "  waited $${WAITED}s..."
    done

    if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
      echo "✅ Spring Boot healthy at http://localhost:8080/health"
      docker logs ticketapp-backend --tail 20
    fi

  else
    # ── EXPECTED on first terraform apply ─────────────────────
    # No image in ECR yet. Instance bootstraps successfully
    # (Docker installed, .env written, CloudWatch running).
    # The ALB /health check will fail until CI/CD pushes
    # an image and SSM deploys it. The ASG health_check_grace_period
    # of 120s gives time. After the first `git push` to main,
    # GitHub Actions will run and deploy the container.
    echo "⚠️  ECR image not found (expected on first terraform apply)."
    echo "    Push code to main branch to trigger CI/CD and deploy the image."
    echo "    Instance is ready and waiting for SSM deploy command."
  fi
else
  echo "ERROR: ECR login failed. Check EC2 IAM role has AmazonEC2ContainerRegistryReadOnly."
fi

echo "=========================================="
echo " user_data.sh complete: $(date)"
echo "=========================================="
