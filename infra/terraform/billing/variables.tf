variable "project_id" {
  description = "ID do projeto study monitorado pelo budget."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "project_id deve ser informado."
  }
}

variable "billing_account_id" {
  description = "ID da conta de faturamento proprietária do budget."
  type        = string

  validation {
    condition     = length(trimspace(var.billing_account_id)) > 0
    error_message = "billing_account_id deve ser informado."
  }
}

variable "budget_amount_usd" {
  description = "Valor mensal do budget em dólares americanos."
  type        = number

  validation {
    condition     = var.budget_amount_usd > 0
    error_message = "budget_amount_usd deve ser maior que zero."
  }
}
