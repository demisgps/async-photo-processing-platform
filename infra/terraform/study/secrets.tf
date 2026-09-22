resource "google_secret_manager_secret" "db_password" {
  project   = var.project_id
  secret_id = local.db_password_secret_id
  labels    = local.common_labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.required["secretmanager.googleapis.com"]]
}
