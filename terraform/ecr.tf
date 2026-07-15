# =============================================================
#  ecr.tf
#
#  ECR repository for the Spring Boot ticketapp backend image.
#
#  WHY THIS EXISTS (moved from workflow CLI call → Terraform):
#    Previously the ECR repo was created on-demand inside
#    docker-build.yml via `aws ecr create-repository`. That works,
#    but it means the repo's config (scan-on-push, lifecycle rules,
#    tag mutability) lives implicitly in CI YAML instead of being
#    versioned, reviewable infra-as-code.
#
#    More importantly: this resource is now part of the BOOTSTRAP
#    phase (see README "Chicken-and-egg fix" section). It has no
#    dependency on VPC/RDS/ALB/ASG, so it can be created in the
#    same -target apply as the GitHub OIDC role — meaning the very
#    first CI/CD run can push an image before any EC2 instance
#    exists, and that image is already sitting in ECR by the time
#    the ASG boots its first instance.
#
#  Bootstrap apply (run once per environment, before first push):
#    terraform apply \
#      -target="aws_iam_openid_connect_provider.github" \
#      -target="aws_iam_role.github_actions_role" \
#      -target="aws_iam_role_policy_attachment.github_ecr_full" \
#      -target="aws_iam_role_policy.github_actions_deploy" \
#      -target="aws_ecr_repository.backend" \
#      -target="aws_ecr_lifecycle_policy.backend"
# =============================================================

resource "aws_ecr_repository" "backend" {
  name                 = "${var.project_name}-backend"   # → ticketapp-backend, matches ECR_REPOSITORY in docker-build.yml
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = local.common_tags
}

# Keep the repo from growing unbounded — untagged images (superseded
# :latest pushes, failed builds, etc.) are cleaned up after 14 days.
# Tagged images (:latest and :<git-sha>) are never touched by this rule,
# so rollback-by-SHA in the deploy step always has what it needs.
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 14 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 14
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep only the most recent 20 images overall (covers :latest + git-sha tags)"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 20
        }
        action = { type = "expire" }
      }
    ]
  })
}
