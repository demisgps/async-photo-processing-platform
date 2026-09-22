variable "project_id" {
  description = "ID do projeto GCP que hospedará o bucket de state."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "project_id deve ser informado."
  }
}

variable "region" {
  description = "Localização principal dos recursos do projeto."
  type        = string
  default     = "us-central1"
}
