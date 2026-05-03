# =============================================================
#  alb.tf
#
#  ALB in front of EC2 ASG instances running the Spring Boot container.
#
#  - HTTPS 443 → forwards to EC2 instances on port 8080
#  - HTTP  80  → 301 redirect to HTTPS
#  - Health: GET /health → {"status":"ok"}  (HealthController.java)
# =============================================================

resource "aws_lb" "ticketapp_alb" {
  name               = "${var.project_name}-alb"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.alb_sg.id]

  subnets = [
    aws_subnet.public_subnet_1.id,
    aws_subnet.public_subnet_2.id
  ]

  tags = merge(local.common_tags, { Name = "${var.project_name}-alb" })

}

# ---------------------------
# Target Group
#
# target_type = "instance" — correct for EC2 ASG (not Fargate)
# port 8080 — Spring Boot server.port in application.properties
# /health   — HealthController.java returns {"status":"ok"} with HTTP 200
# ---------------------------
resource "aws_lb_target_group" "backend_tg" {
  name        = "${var.project_name}-backend-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.ticketapp_vpc.id
  target_type = "instance"   # EC2 instance registration (used by ASG)

  health_check {
    path                = "/health"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Give Spring Boot time to finish Hibernate DDL before draining
  deregistration_delay = 30

 tags = merge(local.common_tags, { Name = "${var.project_name}-backend-tg" })
}

# ---------------------------
# Listener: HTTPS 443 (primary)
# TLS terminated here — Spring Boot receives plain HTTP (USE_HTTPS=false)
# ---------------------------
resource "aws_lb_listener" "https_listener" {
  load_balancer_arn = aws_lb.ticketapp_alb.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_iam_server_certificate.self_signed.arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend_tg.arn
  }
}

# ---------------------------
# Listener: HTTP 80 → 301 redirect to HTTPS
# Ensures COOKIE_SECURE=true works (JWT cookies need HTTPS end-to-end)
# ---------------------------
resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.ticketapp_alb.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}
