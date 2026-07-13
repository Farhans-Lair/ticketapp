# =============================================================
#  security-groups.tf
#
#  Updated traffic flow (task 8 — EC2 now in private subnets):
#    Internet → ALB (80/443, public subnets)
#             → EC2 (8080, private subnets via ALB SG reference)
#             → RDS (3306, private subnets via EC2 SG reference)
#
#  EC2 outbound traffic (ECR, S3, SMTP, Razorpay, Twilio) exits
#  through the NAT Gateway — no direct internet access from EC2.
# =============================================================

# ---------------------------
# ALB Security Group
# ---------------------------
resource "aws_security_group" "alb_sg" {
  name        = "${var.project_name}-alb-sg"
  description = "Allow HTTP/HTTPS inbound from internet to ALB"
  vpc_id      = aws_vpc.ticketapp_vpc.id

  ingress {
    description = "HTTP from internet (redirected to HTTPS by listener)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS from internet - ALB terminates TLS here"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-alb-sg" })
}

# ---------------------------
# EC2 Instance Security Group
# ---------------------------
resource "aws_security_group" "ec2_sg" {
  name        = "${var.project_name}-ec2-sg"
  description = "Allow ALB to EC2 Spring Boot on port 8080"
  vpc_id      = aws_vpc.ticketapp_vpc.id

  ingress {
    description     = "ALB to Spring Boot container on 8080"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb_sg.id]
  }

  egress {
    description = "All outbound - ECR pull, RDS, S3, SMTP, Razorpay API"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-ec2-sg" })
}

# ---------------------------
# RDS MySQL Security Group
# ---------------------------
resource "aws_security_group" "rds_sg" {
  name        = "${var.project_name}-rds-sg"
  description = "Allow MySQL 3306 from EC2 instances only"
  vpc_id      = aws_vpc.ticketapp_vpc.id

  ingress {
    description     = "MySQL from Spring Boot EC2 instances"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2_sg.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-rds-sg" })
}
