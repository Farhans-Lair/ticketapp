#!/bin/bash
# =============================================================
#  user_data.sh
#
#  EC2 first-boot bootstrap for the Spring Boot ticketapp.
#  Runs once when ASG launches a new instance.
#
#  What it does:
#    1. System update + MySQL client + Docker install
#    2. Writes /home/ec2-user/ticketapp-backend/.env
#       (all Spring Boot env vars from terraform.tfvars via launch_template.tf)
#    3. Configures CloudWatch agent to ship app/error logs
#    4. Logs into ECR and pulls the latest Docker image
#    5. Runs the Spring Boot container on port 8080
#    6. Waits for /health to confirm a healthy start
#
#  On CI/CD re-deploy, GitHub Actions SSM command replaces the
#  container (docker rm + docker run) directly — this script
#  only runs on NEW instance launches by the ASG.
#
#  application.properties env var mapping (every var used here):
#    server.port                  → 8080 (hardcoded)
#    server.ssl.enabled           → USE_HTTPS=false  (ALB terminates TLS)
#    cookie.secure                → COOKIE_SECURE=true
#    spring.datasource.url        → DB_HOST / DB_PORT / DB_NAME
#    spring.datasource.username   → DB_USER
#    spring.datasource.password   → DB_PASS
#    jwt.secret                   → JWT_SECRET
#    spring.mail.username         → EMAIL_USER
#    spring.mail.password         → EMAIL_PASS
#    aws.region                   → AWS_REGION
#    aws.s3.bucket                → S3_BUCKET_NAME
#    razorpay.key-id              → RAZORPAY_KEY_ID
#    razorpay.key-secret          → RAZORPAY_KEY_SECRET
#    razorpay.webhook-secret      → RAZORPAY_WEBHOOK_SECRET
#    frontend.url                 → FRONTEND_URL
# =============================================================
set -euxo pipefail
exec > /var/log/user-data.log 2>&1

# ── 1. System update ──────────────────────────────────────────
yum update -y

# ── 2. Remove conflicting MariaDB libs ───────────────────────
yum remove mariadb mariadb-libs -y || true

# ── 3. MySQL 8.0 client ───────────────────────────────────────
# Used for health checks, manual migration runs, and debugging.
yum install https://dev.mysql.com/get/mysql80-community-release-el7-11.noarch.rpm -y
yum-config-manager --enable mysql80-community
yum install mysql-community-client -y

# ── 4. Docker ─────────────────────────────────────────────────
yum install -y docker
systemctl enable docker
systemctl start docker

until docker info >/dev/null 2>&1; do
  sleep 5
done

usermod -aG docker ec2-user

# ── 5. App directory + .env file ──────────────────────────────
APP_DIR=/home/ec2-user/ticketapp-backend
mkdir -p "$APP_DIR/logs"

# All values below are rendered by Terraform template_file from
# terraform.tfvars — no GitHub Secrets involved.
cat > "$APP_DIR/.env" <<ENVEOF
# ── Server ─────────────────────────────────────────────────
SERVER_PORT=8080
USE_HTTPS=false
COOKIE_SECURE=true
FRONTEND_URL=https://${ALB_DNS}

# ── Database ── spring.datasource.* ────────────────────────
DB_HOST=${DB_HOST}
DB_PORT=3306
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASS=${DB_PASS}

# ── JWT ── jwt.secret ───────────────────────────────────────
JWT_SECRET=${JWT_SECRET}

# ── Email ── spring.mail.username / spring.mail.password ───
EMAIL_USER=${EMAIL_USER}
EMAIL_PASS=${EMAIL_PASS}

# ── AWS S3 ── aws.region / aws.s3.bucket ───────────────────
AWS_REGION=${AWS_REGION}
S3_BUCKET_NAME=${S3_BUCKET_NAME}
# Left blank → S3Config.java uses EC2 IAM instance role (s3_ticket_policy)
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# ── Razorpay ── razorpay.key-id / key-secret / webhook-secret
RAZORPAY_KEY_ID=${RAZORPAY_KEY_ID}
RAZORPAY_KEY_SECRET=${RAZORPAY_KEY_SECRET}
RAZORPAY_WEBHOOK_SECRET=${RAZORPAY_WEBHOOK_SECRET}

# ── SSL (disabled — ALB handles TLS) ───────────────────────
SSL_KEYSTORE_TYPE=PKCS12
SSL_KEYSTORE_PATH=classpath:certs/keystore.p12
SSL_KEYSTORE_PASSWORD=changeit
SSL_KEY_ALIAS=1
ENVEOF

chown ec2-user:ec2-user "$APP_DIR/.env"
chmod 600 "$APP_DIR/.env"

# ── 6. CloudWatch Agent ───────────────────────────────────────
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
  -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json

systemctl enable amazon-cloudwatch-agent

# ── 7. ECR Login + Pull + Run ────────────────────────────────
# EC2 IAM role (iam.tf: AmazonEC2ContainerRegistryReadOnly) allows this.
aws ecr get-login-password --region ${AWS_REGION} \
  | docker login --username AWS \
    --password-stdin ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

docker rm -f ticketapp-backend || true

# Spring Boot Dockerfile:
#   Stage 1: maven:3.9.6-eclipse-temurin-17 → mvn package → ticket-booking-backend-1.0.0.jar
#   Stage 2: eclipse-temurin:17-jre-alpine  → java -XX:+UseContainerSupport -jar app.jar
#
# --memory 900m: safe ceiling for t3.small (2GB total)
# -p 8080:8080:  server.port=8080, ALB target group port=8080
# /app/logs:     CloudWatch agent reads app.log + error.log from here
docker run -d \
  --name ticketapp-backend \
  --restart always \
  --env-file "$APP_DIR/.env" \
  -p 8080:8080 \
  -v "$APP_DIR/logs":/app/logs \
  --memory="900m" \
  --memory-swap="900m" \
  ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT_NAME}-backend:latest

# ── 8. Health check wait ──────────────────────────────────────
# Spring Boot + Hibernate DDL cold start ≈ 30–60s.
# HealthController.java → GET /health → {"status":"ok"} → HTTP 200
echo "Waiting for Spring Boot /health..."
MAX_WAIT=120
WAITED=0
until curl -sf http://localhost:8080/health > /dev/null 2>&1; do
  if [ "$WAITED" -ge "$MAX_WAIT" ]; then
    echo "ERROR: Spring Boot did not start within ${MAX_WAIT}s"
    docker logs ticketapp-backend --tail 50
    exit 1
  fi
  sleep 5
  WAITED=$((WAITED + 5))
  echo "  waited ${WAITED}s..."
done

echo "✅ Spring Boot healthy at http://localhost:8080/health"
docker logs ticketapp-backend --tail 20
