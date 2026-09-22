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

variable "api_image" {
  description = "Referência imutável da imagem do photo-api no Artifact Registry."
  type        = string

  validation {
    condition     = length(trimspace(var.api_image)) > 0 && !endswith(lower(trimspace(var.api_image)), ":latest")
    error_message = "api_image deve ser informada e não pode utilizar a tag :latest."
  }
}

variable "consumer_image" {
  description = "Referência imutável da imagem do photo-consumer no Artifact Registry."
  type        = string

  validation {
    condition     = length(trimspace(var.consumer_image)) > 0 && !endswith(lower(trimspace(var.consumer_image)), ":latest")
    error_message = "consumer_image deve ser informada e não pode utilizar a tag :latest."
  }
}
