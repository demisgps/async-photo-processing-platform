data "google_project" "study" {
  project_id = var.project_id
}

locals {
  budget_base_units    = floor(var.budget_amount)
  budget_rounded_nanos = floor(((var.budget_amount - local.budget_base_units) * 1000000000) + 0.5)
  budget_units         = local.budget_base_units + floor(local.budget_rounded_nanos / 1000000000)
  budget_nanos         = local.budget_rounded_nanos % 1000000000
}

resource "google_billing_budget" "study" {
  billing_account = var.billing_account_id
  display_name    = "photo-platform-study-budget"

  budget_filter {
    projects               = ["projects/${data.google_project.study.number}"]
    calendar_period        = "MONTH"
    credit_types_treatment = "EXCLUDE_ALL_CREDITS"
  }

  amount {
    specified_amount {
      currency_code = "BRL"
      units         = local.budget_units
      nanos         = local.budget_nanos
    }
  }

  threshold_rules {
    threshold_percent = 0.50
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 0.80
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 0.90
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 1.00
    spend_basis       = "CURRENT_SPEND"
  }
}
