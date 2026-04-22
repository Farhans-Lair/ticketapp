# =============================================================
#  iam.tf
#
#  IAM roles for EC2 + RDS deployment (no ECS):
#
#  1. EC2 Instance Role — what the Spring Boot EC2 host can do:
#       • Pull Docker image from ECR
#       • S3 PutObject/GetObject for PdfService.java ticket PDFs
#       • CloudWatch agent — push logs and metrics
#       • SSM Session Manager — shell access without SSH keys
#
#  2. GitHub Actions OIDC Role — CI/CD pipeline permissions:
#       • ECR: build and push Docker images
#       • EC2/SSM: send deploy command to ASG instances
#       • ECR: create repository if it doesn't exist
# =============================================================

# ---------------------------
# 1. EC2 Instance Role
# ---------------------------
resource "aws_iam_role" "backend_ec2_role" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Pull Spring Boot Docker image from ECR on instance bootstrap
resource "aws_iam_role_policy_attachment" "ec2_ecr_read" {
  role       = aws_iam_role.backend_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# SSM Session Manager — SSH-free shell access for debugging
resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.backend_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# CloudWatch agent — push EC2 system metrics + Spring Boot log files
resource "aws_iam_role_policy_attachment" "ec2_cloudwatch_agent" {
  role       = aws_iam_role.backend_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# S3 — PdfService.java uploads ticket PDFs; S3Config.java uses IAM role
# when AWS_ACCESS_KEY_ID is blank (it is blank in user_data.sh .env)
resource "aws_iam_role_policy" "ec2_s3_ticket_policy" {
  name = "${var.project_name}-ec2-s3-ticket-policy"
  role = aws_iam_role.backend_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
      Resource = "arn:aws:s3:::${var.s3_bucket_name}/tickets/*"
    }]
  })
}

# CloudWatch — custom booking/payment metric publishing from Spring Boot app
resource "aws_iam_role_policy" "ec2_cloudwatch_put_metrics" {
  name = "${var.project_name}-ec2-cw-put-metrics"
  role = aws_iam_role.backend_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "cloudwatch:PutMetricData",
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams"
      ]
      Resource = "*"
    }]
  })
}

# Instance profile — wraps the role for attachment to the launch template
resource "aws_iam_instance_profile" "backend_instance_profile" {
  name = "${var.project_name}-backend-ec2-profile"
  role = aws_iam_role.backend_ec2_role.name
}

# ---------------------------
# 2. GitHub Actions OIDC Provider
# Keyless auth — no long-lived AWS keys stored anywhere.
# Role ARN is constructed in the workflow from aws_account_id + project_name.
# ---------------------------
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

resource "aws_iam_role" "github_actions_role" {
  name = "${var.project_name}-GitHubActions-Deploy-Role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = aws_iam_openid_connect_provider.github.arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        StringLike = {
          # Only the configured repo can assume this role
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_org}/${var.github_repo}:*"
        }
      }
    }]
  })
}

# ECR — build and push Docker images from GitHub Actions
resource "aws_iam_role_policy_attachment" "github_ecr_full" {
  role       = aws_iam_role.github_actions_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryFullAccess"
}

# EC2 + SSM — find ASG instances by tag and run deploy commands via SSM
resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "${var.project_name}-GitHubActions-EC2-SSM-Deploy"
  role = aws_iam_role.github_actions_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # SSM SendCommand — triggers docker pull + docker run on EC2
        Effect = "Allow"
        Action = [
          "ssm:SendCommand",
          "ssm:GetCommandInvocation",
          "ssm:ListCommandInvocations",
          "ssm:DescribeInstanceInformation"
        ]
        Resource = [
          "arn:aws:ec2:${var.aws_region}:${var.aws_account_id}:instance/*",
          "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
          "arn:aws:ssm:${var.aws_region}:${var.aws_account_id}:*"
        ]
      },
      {
        # Describe EC2 instances — find instance IDs by ASG Name tag
        Effect   = "Allow"
        Action   = ["ec2:DescribeInstances"]
        Resource = "*"
      }
    ]
  })
}
