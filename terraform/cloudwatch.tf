# =============================================================
#  cloudwatch.tf
#
#  Observability for Spring Boot on EC2 + RDS:
#    - Log groups (CloudWatch agent in user_data.sh ships logs here)
#    - SNS alerts topic + email subscription
#    - ALB alarms (5xx, healthy host count)
#    - EC2/ASG alarms (CPU — wired to scale-out/in policies in launch_template.tf)
#    - RDS alarms (CPU, storage, connections)
#    - Log metric filters (payment errors, bookings confirmed)
#    - Dashboard
# =============================================================

# =============================================================
# Log Groups
# CloudWatch agent (user_data.sh) ships:
#   /home/ec2-user/ticketapp-backend/logs/app.log   → /ticketapp/backend
#   /home/ec2-user/ticketapp-backend/logs/error.log → /ticketapp/errors
#   /var/log/user-data.log                          → /ticketapp/ec2-bootstrap
# =============================================================
resource "aws_cloudwatch_log_group" "app_logs" {
  name              = "/ticketapp/backend"
  retention_in_days = 30
  tags              = { Name = "${var.project_name}-app-logs" }
}

resource "aws_cloudwatch_log_group" "error_logs" {
  name              = "/ticketapp/errors"
  retention_in_days = 30
  tags              = { Name = "${var.project_name}-error-logs" }
}

resource "aws_cloudwatch_log_group" "bootstrap_logs" {
  name              = "/ticketapp/ec2-bootstrap"
  retention_in_days = 7
  tags              = { Name = "${var.project_name}-bootstrap-logs" }
}

# =============================================================
# SNS Alert Topic
# =============================================================
resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-alerts"
}

resource "aws_sns_topic_subscription" "email_alert" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# =============================================================
# CloudWatch Alarms
# =============================================================

# --- ALB: high 5xx (BookingController / PaymentController failures) ---
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.project_name}-alb-5xx-errors"
  alarm_description   = "High 5xx on ALB — booking, payment, or cancellation routes failing"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  dimensions          = { LoadBalancer = aws_lb.ticketapp_alb.arn_suffix }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# --- ALB: no healthy EC2 hosts (Spring Boot is down) ---
resource "aws_cloudwatch_metric_alarm" "alb_no_healthy_hosts" {
  alarm_name          = "${var.project_name}-no-healthy-hosts"
  alarm_description   = "Zero healthy EC2 hosts behind ALB — application is down"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "HealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Average"
  threshold           = 1
  dimensions = {
    TargetGroup  = aws_lb_target_group.backend_tg.arn_suffix
    LoadBalancer = aws_lb.ticketapp_alb.arn_suffix
  }
  alarm_actions = [aws_sns_topic.alerts.arn]
}

# --- EC2 ASG: high CPU → triggers scale-out (defined in launch_template.tf) ---
resource "aws_cloudwatch_metric_alarm" "ec2_high_cpu_alert" {
  alarm_name          = "${var.project_name}-ec2-high-cpu-alert"
  alarm_description   = "EC2 CPU > 80% — PDF generation or booking query spike"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  dimensions          = { AutoScalingGroupName = aws_autoscaling_group.backend_asg.name }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# --- RDS: high CPU (slow Hibernate queries under load) ---
resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${var.project_name}-rds-high-cpu"
  alarm_description   = "RDS CPU > 80% — possible slow Hibernate queries or connection storm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  dimensions          = { DBInstanceIdentifier = aws_db_instance.ticketapp_db.identifier }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# --- RDS: low free storage ---
resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "${var.project_name}-rds-low-storage"
  alarm_description   = "RDS free storage < 2 GB — expand allocated_storage in rds.tf"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 2000000000   # 2 GB in bytes
  dimensions          = { DBInstanceIdentifier = aws_db_instance.ticketapp_db.identifier }
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# --- RDS: too many connections (HikariCP pool exhausted) ---
resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${var.project_name}-rds-high-connections"
  alarm_description   = "RDS connections high — HikariCP pool may be exhausted"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 40   # db.t3.micro max_connections ≈ 66
  dimensions          = { DBInstanceIdentifier = aws_db_instance.ticketapp_db.identifier }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# =============================================================
# Log Metric Filters
# Pattern matches Spring Boot log output (logging.level.com.ticketapp=INFO)
# =============================================================

resource "aws_cloudwatch_log_metric_filter" "payment_errors" {
  name           = "${var.project_name}-payment-errors"
  log_group_name = aws_cloudwatch_log_group.error_logs.name
  pattern        = "Payment verification"

  metric_transformation {
    name      = "PaymentVerificationErrors"
    namespace = "${var.project_name}/App"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "payment_error_alarm" {
  alarm_name          = "${var.project_name}-payment-errors"
  alarm_description   = "Payment verification failures — check Razorpay webhook config"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "PaymentVerificationErrors"
  namespace           = "${var.project_name}/App"
  period              = 60
  statistic           = "Sum"
  threshold           = 3
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_log_metric_filter" "bookings_confirmed" {
  name           = "${var.project_name}-bookings-confirmed"
  log_group_name = aws_cloudwatch_log_group.app_logs.name
  pattern        = "Booking confirmed"

  metric_transformation {
    name      = "BookingsConfirmed"
    namespace = "${var.project_name}/App"
    value     = "1"
  }
}

resource "aws_cloudwatch_log_metric_filter" "cancellations" {
  name           = "${var.project_name}-cancellations"
  log_group_name = aws_cloudwatch_log_group.app_logs.name
  pattern        = "Cancellation"

  metric_transformation {
    name      = "CancellationRequests"
    namespace = "${var.project_name}/App"
    value     = "1"
  }
}

# =============================================================
# CloudWatch Dashboard
# =============================================================
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"; width = 12; height = 6
        properties = {
          title   = "ALB — Requests & 5xx Errors"
          region  = var.aws_region
          metrics = [
            ["AWS/ApplicationELB", "RequestCount",              "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix],
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix],
            ["AWS/ApplicationELB", "TargetResponseTime",        "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix]
          ]
          period = 60; stat = "Sum"; view = "timeSeries"
        }
      },
      {
        type = "metric" width = 12 height = 6
        properties = {
          title   = "ALB — Healthy Host Count"
          region  = var.aws_region
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", aws_lb_target_group.backend_tg.arn_suffix, "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix]
          ]
          period = 60; stat = "Average"; view = "timeSeries"
        }
      },
      {
        type = "metric"; width = 12; height = 6
        properties = {
          title   = "EC2 ASG — CPU Utilization"
          region  = var.aws_region
          metrics = [
            ["AWS/EC2", "CPUUtilization", "AutoScalingGroupName", aws_autoscaling_group.backend_asg.name]
          ]
          period = 60; stat = "Average"; view = "timeSeries"
        }
      },
      {
        type = "metric"; width = 12; height = 6
        properties = {
          title   = "RDS — CPU & DB Connections"
          region  = var.aws_region
          metrics = [
            ["AWS/RDS", "CPUUtilization",      "DBInstanceIdentifier", aws_db_instance.ticketapp_db.identifier],
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.ticketapp_db.identifier]
          ]
          period = 60; stat = "Average"; view = "timeSeries"
        }
      },
      {
        type = "metric"; width = 12; height = 6
        properties = {
          title   = "RDS — Free Storage Space"
          region  = var.aws_region
          metrics = [
            ["AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", aws_db_instance.ticketapp_db.identifier]
          ]
          period = 300; stat = "Average"; view = "timeSeries"
        }
      },
      {
        type = "metric"; width = 12; height = 6
        properties = {
          title   = "App — Bookings vs Payment Errors vs Cancellations"
          region  = var.aws_region
          metrics = [
            ["${var.project_name}/App", "BookingsConfirmed"],
            ["${var.project_name}/App", "PaymentVerificationErrors"],
            ["${var.project_name}/App", "CancellationRequests"]
          ]
          period = 300; stat = "Sum"; view = "timeSeries"
        }
      },
      {
        type = "log"; width = 24; height = 6
        properties = {
          title = "Recent Application Errors (last 3 hours)"
          query = "SOURCE '/ticketapp/errors' | fields @timestamp, @message | sort @timestamp desc | limit 50"
          view  = "table"
        }
      }
    ]
  })
}
