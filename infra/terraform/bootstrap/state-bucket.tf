resource "google_storage_bucket" "terraform_state" {
  name     = "${var.project_id}-tfstate"
  project  = var.project_id
  location = var.region

  force_destroy               = false
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  versioning {
    enabled = true
  }

  soft_delete_policy {
    retention_duration_seconds = 604800
  }

  labels = {
    environment = "study"
    managed_by  = "terraform-bootstrap"
    purpose     = "terraform-state"
  }

  lifecycle {
    prevent_destroy = true
  }
}
