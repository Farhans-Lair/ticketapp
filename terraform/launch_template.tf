# =============================================================
#  launch_template.tf
#
#  EC2 Launch Template for the Spring Boot ticketapp running
#  as a Docker container on EC2.
#
#  The Auto Scaling Group (ASG) and all scaling resources
#  are defined in asg.tf.
#
#  Flow:
#    terraform apply → Launch Template created with rendered user_data.sh
#    ASG (asg.tf) launches EC2 → user_data.sh runs:
#      - Installs Docker, writes .env, starts CloudWatch agent
#      - Attempts ECR pull (skipped gracefully if no image yet)
#    First `git push main` → GitHub Actions CI/CD:
#      - Builds image, pushes to ECR
#      - SSM SendCommand: docker pull + docker run on EC2
#      - Spring Boot starts → ALB /health passes → traffic flows
# =============================================================

data "aws_caller_identity" "current" {}

# ---------------------------
# Render user_data.sh with all Spring Boot env vars
# All values sourced from terraform.tfvars
# ---------------------------
data "template_file" "user_data" {
  template = file("${path.module}/user_data.sh")

  vars = {
    ACCOUNT_ID   = data.aws_caller_identity.current.account_id
    AWS_REGION   = var.aws_region
    PROJECT_NAME = var.project_name

    # RDS address is resolved after aws_db_instance is created
    # Maps to spring.datasource.url → ${DB_HOST} in application.properties
    DB_HOST = aws_db_instance.ticketapp_db.address
    DB_NAME = var.db_name
    DB_USER = var.db_username
    DB_PASS = var.db_password   # application.properties uses DB_PASS (not DB_PASSWORD)

    JWT_SECRET              = var.jwt_secret
    RAZORPAY_KEY_ID         = var.razorpay_key_id
    RAZORPAY_KEY_SECRET     = var.razorpay_key_secret

    EMAIL_USER = var.email_user
    EMAIL_PASS = var.email_pass

    S3_BUCKET_NAME = var.s3_bucket_name

    # ALB DNS → written as FRONTEND_URL in .env
    # frontend.url=${FRONTEND_URL} used by WebConfig.java CORS
    ALB_DNS = aws_lb.ticketapp_alb.dns_name

    # FIX: MAX_WAIT removed from here.
    # It was a bash variable hardcoded inside user_data.sh (MAX_WAIT=180),
    # NOT a Terraform template variable. Passing it here caused a mismatch
    # where Terraform would try to substitute ${MAX_WAIT} while bash also
    # defined it, leading to conflicts during template rendering.
    # The value is controlled directly in user_data.sh as a bash variable.
  }
}

# ---------------------------
# Latest Amazon Linux 2 AMI
# ---------------------------
data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
}

# ---------------------------
# EC2 Launch Template
#
# t3.small (2 GB RAM) required for Spring Boot JVM:
#   Base JVM:              ~256 MB
#   Hibernate + HikariCP:  ~128 MB
#   PdfService (fonts):    ~150 MB peak
#   Docker + OS:           ~300 MB
#   Total:                 ~834 MB → t3.micro (1 GB) is too tight
# ---------------------------
resource "aws_launch_template" "backend_lt" {
  name_prefix   = "${var.project_name}-backend-"
  image_id      = data.aws_ami.amazon_linux.id
  instance_type = var.ec2_instance_type

  iam_instance_profile {
    name = aws_iam_instance_profile.backend_instance_profile.name
  }

  network_interfaces {
    security_groups             = [aws_security_group.ec2_sg.id]
    associate_public_ip_address = true   # needed to reach ECR without NAT Gateway
  }

  user_data = base64encode(data.template_file.user_data.rendered)

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name = "${var.project_name}-backend"
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}
