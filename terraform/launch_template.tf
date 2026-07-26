# launch_template.tf EC2 Launch Template for the Spring Boot ticketapp running as a Docker container on EC2.

data "aws_caller_identity" "current" {}

# Render user_data.sh with all Spring Boot env vars All values sourced from terraform.tfvars
data "template_file" "user_data" {
  template = file("${path.module}/user_data.sh")

  vars = {
    # Only non-secret, non-$ values are injected via Terraform template.
    ACCOUNT_ID   = data.aws_caller_identity.current.account_id
    AWS_REGION   = var.aws_region
    PROJECT_NAME = var.project_name
  }
}

# Latest Amazon Linux 2 AMI
data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
}

# EC2 Launch Template t3.small (2 GB RAM) required for Spring Boot JVM: Base JVM: ~256 MB
resource "aws_launch_template" "backend_lt" {
  name_prefix   = "${var.project_name}-backend-"
  image_id      = data.aws_ami.amazon_linux.id
  instance_type = var.ec2_instance_type

  iam_instance_profile {
    name = aws_iam_instance_profile.backend_instance_profile.name
  }

  network_interfaces {
    security_groups             = [aws_security_group.ec2_sg.id]
    # false: instances live in private subnets and reach ECR/S3/Razorpay/ Twilio via the NAT Gateway (see vpc.tf
    associate_public_ip_address = false
  }

  # Enforce IMDSv2 (session-token-based metadata requests only).
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"    # IMDSv2 only, IMDSv1 disabled
    http_put_response_hop_limit = 1
  }

  user_data = base64encode(data.template_file.user_data.rendered)

  tag_specifications {
    resource_type = "instance"
    tags = merge(local.common_tags, {
      Name = "${var.project_name}-backend"
    })

  }

  # Ensure all SSM parameters exist before any EC2 instance launches.
  depends_on = [
    aws_ssm_parameter.db_host,
    aws_ssm_parameter.db_name,
    aws_ssm_parameter.db_user,
    aws_ssm_parameter.db_pass,
    aws_ssm_parameter.jwt_access_secret,
    aws_ssm_parameter.jwt_refresh_secret,
    aws_ssm_parameter.jwt_session_secret,
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
