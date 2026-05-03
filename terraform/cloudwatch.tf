# =============================================================
#  cloudwatch.tf
# =============================================================

# Log Groups
resource "aws_cloudwatch_log_group" "app_logs" {
  name              = "/ticketapp/backend"
  retention_in_days = 30
  tags = merge(local.common_tags, { Name = "${var.project_name}-app-logs" })
}

resource "aws_cloudwatch_log_group" "error_logs" {
  name              = "/ticketapp/errors"
  retention_in_days = 30
  tags = merge(local.common_tags, { Name = "${var.project_name}-error-logs" })
}

resource "aws_cloudwatch_log_group" "bootstrap_logs" {
  name              = "/ticketapp/ec2-bootstrap"
  retention_in_days = 7
  tags = merge(local.common_tags, { Name = "${var.project_name}-bootstrap-logs" })
}

# SNS Alert Topic
resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-alerts"
}

resource "aws_sns_topic_subscription" "email_alert" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# CloudWatch Alarms
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.project_name}-alb-5xx-errors"
  alarm_description   = "High 5xx on ALB"
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

resource "aws_cloudwatch_metric_alarm" "alb_no_healthy_hosts" {
  alarm_name          = "${var.project_name}-no-healthy-hosts"
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

resource "aws_cloudwatch_metric_alarm" "ec2_high_cpu_alert" {
  alarm_name          = "${var.project_name}-ec2-high-cpu-alert"
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

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${var.project_name}-rds-high-cpu"
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

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "${var.project_name}-rds-low-storage"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 2000000000
  dimensions          = { DBInstanceIdentifier = aws_db_instance.ticketapp_db.identifier }
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${var.project_name}-rds-high-connections"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 40
  dimensions          = { DBInstanceIdentifier = aws_db_instance.ticketapp_db.identifier }
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# Log Metric Filters
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

# CloudWatch Dashboard
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "ALB — Requests & 5xx Errors"
          region  = var.aws_region
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix],
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix],
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix]
          ]
          period = 60
          stat   = "Sum"
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "ALB — Healthy Host Count"
          region  = var.aws_region
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", aws_lb_target_group.backend_tg.arn_suffix, "LoadBalancer", aws_lb.ticketapp_alb.arn_suffix]
          ]
          period = 60
          stat   = "Average"
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "EC2 ASG — CPU Utilization"
          region  = var.aws_region
          metrics = [
            ["AWS/EC2", "CPUUtilization", "AutoScalingGroupName", aws_autoscaling_group.backend_asg.name]
          ]
          period = 60
          stat   = "Average"
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "RDS — CPU & DB Connections"
          region  = var.aws_region
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.ticketapp_db.identifier],
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.ticketapp_db.identifier]
          ]
          period = 60
          stat   = "Average"
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "RDS — Free Storage Space"
          region  = var.aws_region
          metrics = [
            ["AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", aws_db_instance.ticketapp_db.identifier]
          ]
          period = 300
          stat   = "Average"
          view   = "timeSeries"
        }
      },
      {
        type   = "log"
        width  = 24
        height = 6
        properties = {
          title = "Recent Application Errors (last 3 hours)"
          query = "SOURCE '/ticketapp/errors' | fields @timestamp, @message | sort @timestamp desc | limit 50"
          view  = "table"
        }
      }
    ]
  })
}