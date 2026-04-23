# =============================================================
#  launch_template.tf
#
#  EC2 Launch Template + Auto Scaling Group for the Spring Boot
#  ticketapp running as a Docker container on EC2.
#
#  Flow:
#    terraform apply → Launch Template created with rendered user_data.sh
#    ASG launches EC2 → user_data.sh runs:
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

    MAX_WAIT       = "180"
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

# ---------------------------
# Auto Scaling Group
# ---------------------------
resource "aws_autoscaling_group" "backend_asg" {
  name = "${var.project_name}-backend-asg"

  desired_capacity = var.asg_desired_capacity
  min_size         = var.asg_min_size
  max_size         = var.asg_max_size

  vpc_zone_identifier = [
    aws_subnet.public_subnet_1.id,
    aws_subnet.public_subnet_2.id
  ]

  launch_template {
    id      = aws_launch_template.backend_lt.id
    version = "$Latest"
  }

  target_group_arns = [aws_lb_target_group.backend_tg.arn]

  health_check_type = "ELB"

  # FIX: Extended grace period.
  # On first apply there is no ECR image yet, so the container won't start
  # until CI/CD runs. 600s (10 min) gives enough time for:
  #   - user_data.sh setup (~3 min)
  #   - CI/CD push + SSM deploy + Spring Boot cold start (~5 min)
  # After first image is deployed subsequent starts use the cached image
  # and are much faster (~90s). The grace period only applies to new instances.
  health_check_grace_period = 600

  # Rolling refresh when launch template changes (e.g. new user_data)
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
    }
  }

  tag {
    key                 = "Name"
    value               = "${var.project_name}-backend"
    propagate_at_launch = true
  }
}

# ---------------------------
# ASG CPU-based Scaling Policies
# Scale out: CPU > 70% for 2 min (PDF generation + JPA queries are CPU-heavy)
# Scale in:  CPU < 20% for 10 min
# ---------------------------
resource "aws_autoscaling_policy" "scale_out" {
  name                   = "${var.project_name}-scale-out"
  autoscaling_group_name = aws_autoscaling_group.backend_asg.name
  adjustment_type        = "ChangeInCapacity"
  scaling_adjustment     = 1
  cooldown               = 120
}

resource "aws_autoscaling_policy" "scale_in" {
  name                   = "${var.project_name}-scale-in"
  autoscaling_group_name = aws_autoscaling_group.backend_asg.name
  adjustment_type        = "ChangeInCapacity"
  scaling_adjustment     = -1
  cooldown               = 300
}

resource "aws_cloudwatch_metric_alarm" "ec2_high_cpu_scale_out" {
  alarm_name          = "${var.project_name}-ec2-high-cpu-scale-out"
  alarm_description   = "Scale out: EC2 CPU > 70% for 2 minutes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Average"
  threshold           = 70

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.backend_asg.name
  }

  alarm_actions = [aws_autoscaling_policy.scale_out.arn]
}

resource "aws_cloudwatch_metric_alarm" "ec2_low_cpu_scale_in" {
  alarm_name          = "${var.project_name}-ec2-low-cpu-scale-in"
  alarm_description   = "Scale in: EC2 CPU < 20% for 10 minutes"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 10
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Average"
  threshold           = 20

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.backend_asg.name
  }

  alarm_actions = [aws_autoscaling_policy.scale_in.arn]
}
