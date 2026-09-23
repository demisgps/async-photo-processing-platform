output "budget_name" {
  description = "Nome completo do budget mensal do projeto study."
  value       = google_billing_budget.study.name
}

output "budget_display_name" {
  description = "Nome de exibição do budget mensal do projeto study."
  value       = google_billing_budget.study.display_name
}
