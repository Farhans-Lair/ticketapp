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
    # Only non-secret, non-$ values are injected via Terraform template.
    # All secrets (DB_PASS, JWT_SECRET, etc.) are stored in SSM Parameter
    # Store (ssm_parameters.tf) and fetched at runtime by user_data.sh.
    # This avoids the heredoc/echo $ expansion conflict entirely.
    ACCOUNT_ID   = data.aws_caller_identity.current.account_id
    AWS_REGION   = var.aws_region
    PROJECT_NAME = var.project_name
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

  # Ensure all SSM parameters exist before any EC2 instance launches.
  # Without this, Terraform may create the ASG and boot an instance while
  # SSM parameters are still being created → user_data.sh gets
  # ParameterNotFound on every fetch() call → .env is written with empty
  # values → Spring Boot cannot connect to RDS → health check fails.
  depends_on = [
    aws_ssm_parameter.db_host,
    aws_ssm_parameter.db_name,
    aws_ssm_parameter.db_user,
    aws_ssm_parameter.db_pass,
    aws_ssm_parameter.jwt_secret,
    aws_ssm_parameter.razorpay_key_id,
    aws_ssm_parameter.razorpay_key_secret,
    aws_ssm_parameter.email_user,
    aws_ssm_parameter.email_pass,
    aws_ssm_parameter.s3_bucket,
    aws_ssm_parameter.alb_dns,
  ]

  lifecycle {
    create_before_destroy = true
  }
}
