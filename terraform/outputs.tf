output "rds_endpoint" {
  description = "RDS MySQL endpoint — injected as DB_HOST in user_data.sh automatically"
  value       = aws_db_instance.ticketapp_db.endpoint
}

output "alb_dns_name" {
  description = "ALB DNS name — hardcode this in the workflow env block as ALB_DNS"
  value       = aws_lb.ticketapp_alb.dns_name
}

output "app_url" {
  description = "Application URL (click through self-signed cert warning once)"
  value       = "https://${aws_lb.ticketapp_alb.dns_name}"
}

output "ecr_repository_url" {
  description = "ECR repository URL — used in docker-build.yml to push and pull images"
  value       = "https://${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${var.project_name}-backend"
}

output "github_actions_role_arn" {
  description = "GitHub Actions OIDC role ARN — constructed in workflow, no secrets needed"
  value       = aws_iam_role.github_actions_role.arn
}

output "asg_name" {
  description = "ASG name — used by GitHub Actions SSM deploy to find instance IDs"
  value       = aws_autoscaling_group.backend_asg.name
}

output "iam_certificate_name" {
  description = "IAM server certificate name on the ALB HTTPS listener"
  value       = aws_iam_server_certificate.self_signed.name
}

output "s3_bucket_name" {
  description = "S3 bucket for ticket PDFs (PdfService.java)"
  value       = aws_s3_bucket.ticket_pdfs.bucket
}

output "cloudwatch_dashboard_url" {
  description = "CloudWatch dashboard link"
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${var.project_name}-dashboard"
}
