variable "project_id" {
  description = "ID do projeto GCP do ambiente de estudo."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "project_id deve ser informado."
  }
}

variable "region" {
  description = "Região principal dos recursos do ambiente de estudo."
  type        = string
  default     = "us-central1"
}
