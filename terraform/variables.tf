# =============================================================
#  variables.tf
#
#  All variable VALUES live in terraform.tfvars (never committed).
#  This file only declares types, descriptions, and safe defaults.
#  Sensitive variables have no default so Terraform errors loudly
#  if terraform.tfvars is missing or incomplete.
# =============================================================

# ── GitHub / CI-CD ────────────────────────────────────────────────────────────
variable "github_org" {
  description = "GitHub organisation or username that owns the repo"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name (e.g. ticketapp)"
  type        = string
}

# ── AWS ───────────────────────────────────────────────────────────────────────
variable "aws_account_id" {
  description = "12-digit AWS account ID"
  type        = string
}

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "ap-south-1"
}

# ── Project ───────────────────────────────────────────────────────────────────
variable "project_name" {
  description = "Short lowercase prefix applied to every resource name"
  type        = string
  default     = "ticketapp"
}

# ── Networking ────────────────────────────────────────────────────────────────
variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "public_subnet_1_cidr" {
  description = "Public subnet AZ-a (ALB + EC2 instances)"
  type        = string
  default     = "10.0.1.0/24"
}

variable "public_subnet_2_cidr" {
  description = "Public subnet AZ-b (ALB + EC2 instances)"
  type        = string
  default     = "10.0.2.0/24"
}

variable "private_subnet_1_cidr" {
  description = "Private subnet AZ-a (RDS only — no internet route)"
  type        = string
  default     = "10.0.3.0/24"
}

variable "private_subnet_2_cidr" {
  description = "Private subnet AZ-b (RDS only — no internet route)"
  type        = string
  default     = "10.0.4.0/24"
}

# ── EC2 / ASG ─────────────────────────────────────────────────────────────────
variable "ec2_instance_type" {
  description = "EC2 instance type for Spring Boot. t3.small (2GB) recommended — JVM + Hibernate + PdfService needs > 1GB"
  type        = string
  default     = "t3.small"
}

variable "asg_desired_capacity" {
  description = "Normal number of running EC2 instances"
  type        = number
  default     = 1
}

variable "asg_min_size" {
  description = "Minimum EC2 instances (keep at 1 so the app is always available)"
  type        = number
  default     = 1
}

variable "asg_max_size" {
  description = "Maximum EC2 instances during scale-out"
  type        = number
  default     = 3
}

# ── RDS / Database ────────────────────────────────────────────────────────────
# Env var names match application.properties exactly:
#   spring.datasource.url      → DB_HOST, DB_PORT, DB_NAME
#   spring.datasource.username → DB_USER
#   spring.datasource.password → DB_PASS   ← DB_PASS not DB_PASSWORD
variable "db_name" {
  description = "MySQL database name (maps to DB_NAME)"
  type        = string
  default     = "ticket_db"
}

variable "db_username" {
  description = "MySQL master username (maps to DB_USER)"
  type        = string
  default     = "ticket_user"
}

variable "db_password" {
  description = "MySQL master password (maps to DB_PASS). Set in terraform.tfvars"
  type        = string
  sensitive   = true
}

# ── Application secrets ───────────────────────────────────────────────────────
variable "jwt_secret" {
  description = "JWT signing secret — min 32 chars (maps to JWT_SECRET)"
  type        = string
  sensitive   = true
}

variable "razorpay_key_id" {
  description = "Razorpay Key ID (maps to RAZORPAY_KEY_ID)"
  type        = string
  sensitive   = true
}

variable "razorpay_key_secret" {
  description = "Razorpay Key Secret (maps to RAZORPAY_KEY_SECRET)"
  type        = string
  sensitive   = true
}

variable "razorpay_webhook_secret" {
  description = "Razorpay Webhook Secret (maps to RAZORPAY_WEBHOOK_SECRET)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "email_user" {
  description = "Gmail address for OTP/booking emails (maps to EMAIL_USER)"
  type        = string
  sensitive   = true
}

variable "email_pass" {
  description = "Gmail App Password (maps to EMAIL_PASS)"
  type        = string
  sensitive   = true
}

# ── S3 ────────────────────────────────────────────────────────────────────────
variable "s3_bucket_name" {
  description = "S3 bucket name for ticket PDF storage (maps to S3_BUCKET_NAME)"
  type        = string
}

# ── TLS / Certificate ─────────────────────────────────────────────────────────
variable "cert_chain_path" {
  description = "Absolute local path to mkcert CA root cert (rootCA.pem). Get via: mkcert -CAROOT"
  type        = string
}

# ── Monitoring ────────────────────────────────────────────────────────────────
variable "alert_email" {
  description = "Email address for CloudWatch alarm SNS notifications"
  type        = string
}
