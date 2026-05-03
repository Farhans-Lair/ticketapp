# =============================================================
#  tags.tf — Resource Tagging Strategy
#
#  All AWS resources in this project are tagged with a consistent
#  set of labels. Tags serve three purposes:
#
#    1. Cost allocation  — AWS Cost Explorer can group and filter
#       spend by any tag key. Set up tag-based cost allocation keys
#       in the Billing console (Billing → Cost allocation tags →
#       activate "project", "env", "owner").
#
#    2. Operational visibility  — CloudWatch, Config, and the EC2
#       console all surface tags. Filtering by env=prod quickly
#       isolates production resources from dev/staging.
#
#    3. Automation / policy  — IAM permission boundaries and
#       AWS Config rules can target resources by tag. For example,
#       an SCP can deny deletion of any resource tagged env=prod
#       without an approval tag.
#
#  HOW TO USE
#  ──────────
#  Every resource in this project merges the common tags via:
#
#    tags = merge(local.common_tags, { Name = "..." })
#
#  The Name tag is always set per-resource (unique identifier).
#  All other tags come from local.common_tags below.
#
#  ADDING A NEW RESOURCE
#  ─────────────────────
#  Replace:   tags = { Name = "${var.project_name}-my-resource" }
#  With:      tags = merge(local.common_tags, { Name = "${var.project_name}-my-resource" })
#
#  SETTING TAG VALUES
#  ──────────────────
#  Add to terraform.tfvars:
#    environment  = "prod"
#    owner        = "yourteam"
#    cost_centre  = "eng-backend"
#
#  Variables are declared at the bottom of this file.
# =============================================================

locals {
  # ── Common tags applied to every resource ────────────────────
  # Merge this into every resource's tags block:
  #   tags = merge(local.common_tags, { Name = "..." })
  common_tags = {
    project     = var.project_name   # e.g. "ticketapp"
    env         = var.environment    # e.g. "prod" | "staging" | "dev"
    owner       = var.owner          # e.g. "backend-team"
    cost_centre = var.cost_centre    # e.g. "eng-backend"
    managed_by  = "terraform"        # always set — identifies IaC-managed resources
    repo        = "github.com/${var.github_org}/${var.github_repo}"
  }
}

# ── Tag-related variable declarations ────────────────────────

variable "environment" {
  description = "Deployment environment. Used in cost allocation and resource filtering. E.g. prod, staging, dev."
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["prod", "staging", "dev"], var.environment)
    error_message = "environment must be one of: prod, staging, dev."
  }
}

variable "owner" {
  description = "Team or individual responsible for this stack. Used in cost allocation tags."
  type        = string
  default     = "backend-team"
}

variable "cost_centre" {
  description = "Cost centre / billing code for AWS Cost Explorer grouping. Set in terraform.tfvars."
  type        = string
  default     = "eng-backend"
}
