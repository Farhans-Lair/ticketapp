# tags.tf — Resource Tagging Strategy All AWS resources in this project are tagged with a consistent

locals {
  # Common tags applied to every resource Merge this into every resource's tags block: tags = merge(local.common_tags,
  common_tags = {
    project     = var.project_name     # e.g. "ticketapp"
    env         = var.environment      # e.g. "prod" | "staging" | "dev"
    owner       = var.owner            # e.g. "backend-team"
    cost_centre = var.cost_centre      # e.g. "eng-backend"
    managed_by  = "terraform"          # always set — identifies IaC-managed resources
    repo        = "github.com/${var.github_org}/${var.github_repo}"
  }
}

# Tag-related variable declarations

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
