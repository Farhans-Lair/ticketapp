# =============================================================
#  ssm_parameters.tf
#
#  Stores all application secrets in AWS SSM Parameter Store.
#
#  WHY SSM Parameter Store instead of hardcoding in workflow:
#    - Secrets live in AWS (encrypted at rest with KMS default key)
#    - user_data.sh reads them on EC2 boot via IAM role — no file needed
#    - The GitHub Actions deploy step reads them via OIDC role — no
#      GitHub Secrets or hardcoded values in docker-build.yml needed
#    - Rotating a secret = update terraform.tfvars + terraform apply
#      The next deploy automatically picks up the new value
#
#  Path convention: /ticketapp/<name>
#  All parameters are SecureString (encrypted).
#  EC2 role and GitHub Actions role both get ssm:GetParameter on
#  arn:aws:ssm:region:account:parameter/ticketapp/* (see iam.tf).
# =============================================================

resource "aws_ssm_parameter" "db_host" {
  name        = "/ticketapp/DB_HOST"
  type        = "SecureString"
  value       = aws_db_instance.ticketapp_db.address
  description = "RDS endpoint hostname for Spring datasource"
}

resource "aws_ssm_parameter" "db_name" {
  name        = "/ticketapp/DB_NAME"
  type        = "SecureString"
  value       = var.db_name
  description = "MySQL database name"
}

resource "aws_ssm_parameter" "db_user" {
  name        = "/ticketapp/DB_USER"
  type        = "SecureString"
  value       = var.db_username
  description = "MySQL master username"
}

resource "aws_ssm_parameter" "db_pass" {
  name        = "/ticketapp/DB_PASS"
  type        = "SecureString"
  value       = var.db_password
  description = "MySQL master password"
}

resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/ticketapp/JWT_SECRET"
  type        = "SecureString"
  value       = var.jwt_secret
  description = "JWT signing secret (min 32 chars)"
}

resource "aws_ssm_parameter" "razorpay_key_id" {
  name        = "/ticketapp/RAZORPAY_KEY_ID"
  type        = "SecureString"
  value       = var.razorpay_key_id
  description = "Razorpay Key ID"
}

resource "aws_ssm_parameter" "razorpay_key_secret" {
  name        = "/ticketapp/RAZORPAY_KEY_SECRET"
  type        = "SecureString"
  value       = var.razorpay_key_secret
  description = "Razorpay Key Secret"
}

resource "aws_ssm_parameter" "email_user" {
  name        = "/ticketapp/EMAIL_USER"
  type        = "SecureString"
  value       = var.email_user
  description = "Gmail SMTP username"
}

resource "aws_ssm_parameter" "email_pass" {
  name        = "/ticketapp/EMAIL_PASS"
  type        = "SecureString"
  value       = var.email_pass
  description = "Gmail App Password"
}

resource "aws_ssm_parameter" "alb_dns" {
  name        = "/ticketapp/ALB_DNS"
  type        = "String"
  value       = aws_lb.ticketapp_alb.dns_name
  description = "ALB DNS name for FRONTEND_URL and health checks"
}

resource "aws_ssm_parameter" "s3_bucket" {
  name        = "/ticketapp/S3_BUCKET_NAME"
  type        = "String"
  value       = var.s3_bucket_name
  description = "S3 bucket name for ticket PDFs"
}
