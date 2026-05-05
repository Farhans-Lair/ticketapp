# =============================================================
#  iam.tf (FIXED)
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

# ECR pull access
resource "aws_iam_role_policy_attachment" "ec2_ecr_read" {
  role       = aws_iam_role.backend_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# SSM (required for SendCommand to work!)
resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.backend_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# CloudWatch logs + metrics
resource "aws_iam_role_policy_attachment" "ec2_cloudwatch_agent" {
  role       = aws_iam_role.backend_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# S3 access for ticket PDFs
# SSM Parameter Store read — EC2 reads /ticketapp/* on boot and at runtime
resource "aws_iam_role_policy" "ec2_ssm_params_read" {
  name = "${var.project_name}-ec2-ssm-params-read"
  role = aws_iam_role.backend_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
      Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/ticketapp/*"
    }]
  })
}

resource "aws_iam_role_policy" "ec2_s3_ticket_policy" {
  name = "${var.project_name}-ec2-s3-ticket-policy"
  role = aws_iam_role.backend_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
      # tickets/*       — booking ticket PDFs
      # cancellations/* — cancellation invoice PDFs
      # events/images/* — event cover images uploaded via ImageController
      Resource = [
        "arn:aws:s3:::${var.s3_bucket_name}/tickets/*",
        "arn:aws:s3:::${var.s3_bucket_name}/cancellations/*",
        "arn:aws:s3:::${var.s3_bucket_name}/events/images/*"
      ]
    }]
  })
}

# Custom CloudWatch metrics
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

# Instance profile
resource "aws_iam_instance_profile" "backend_instance_profile" {
  name = "${var.project_name}-backend-ec2-profile"
  role = aws_iam_role.backend_ec2_role.name
}

# ---------------------------
# 2. GitHub OIDC Provider
# ---------------------------
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

# ---------------------------
# 3. GitHub Actions Role
# ---------------------------
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
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_org}/${var.github_repo}:*"
        }
      }
    }]
  })
}

# ECR full access for build & push
resource "aws_iam_role_policy_attachment" "github_ecr_full" {
  role       = aws_iam_role.github_actions_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryFullAccess"
}

# ---------------------------
# 🚀 FIXED: EC2 + SSM Deploy Policy
# ---------------------------
resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "${var.project_name}-GitHubActions-EC2-SSM-Deploy"
  role = aws_iam_role.github_actions_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [

      # 🔥 CRITICAL FIX — allow SSM fully (prevents AccessDenied)
      {
        Effect = "Allow"
        Action = [
          "ssm:SendCommand",
          "ssm:GetCommandInvocation",
          "ssm:ListCommandInvocations",
          "ssm:DescribeInstanceInformation"
        ]
        Resource = "*"
      },

      # SSM Parameter Store read — deploy step fetches secrets to write .env on EC2
      # No hardcoding or GitHub Secrets needed: values come from AWS at deploy time
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/ticketapp/*"
      },

      # Required to discover EC2 instances
      {
        Effect = "Allow"
        Action = ["ec2:DescribeInstances"]
        Resource = "*"
      }
    ]
  })
}