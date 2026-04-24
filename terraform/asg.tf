# =============================================================
#  asg.tf
#
#  Auto Scaling Group (ASG) for the Spring Boot ticketapp EC2
#  instances. The Launch Template is defined in launch_template.tf.
#
#  Includes:
#    - aws_autoscaling_group       : ASG referencing the Launch Template
#    - aws_autoscaling_policy      : CPU-based scale-out and scale-in policies
#    - aws_cloudwatch_metric_alarm : CloudWatch alarms that trigger the policies
#
#  Design decisions:
#    - health_check_grace_period = 600s (10 min):
#        On first terraform apply there is no ECR image yet, so the container
#        won't start until CI/CD runs. 600s covers:
#          - user_data.sh setup   (~3 min)
#          - CI/CD push + SSM deploy + Spring Boot cold start (~5 min)
#        After the first image is deployed, subsequent starts use the cached
#        image and are much faster (~90s). Grace period only applies to new
#        instances, so it does not affect steady-state behaviour.
#    - health_check_type = "ELB":
#        Uses the ALB /health target group check so unhealthy instances
#        are automatically replaced by the ASG.
#    - Rolling instance_refresh (min_healthy_percentage = 50):
#        When the launch template changes (e.g. new user_data / env vars),
#        ASG replaces instances one-by-one keeping at least 50% healthy.
# =============================================================

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

  # Extended grace period — see module header for rationale.
  health_check_grace_period = 600

  # Rolling refresh when launch template changes (e.g. new user_data or env vars)
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
#
# Scale out: CPU > 70% for 2 consecutive 1-min periods
#   → PDF generation and JPA queries are CPU-heavy; allow burst headroom.
# Scale in:  CPU < 20% for 10 consecutive 1-min periods
#   → Conservative cooldown prevents thrashing on short traffic dips.
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

# ---------------------------
# CloudWatch Alarms → trigger ASG policies
# ---------------------------
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
