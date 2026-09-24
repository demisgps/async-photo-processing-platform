resource "google_sql_database_instance" "main" {
  project          = var.project_id
  name             = local.sql_instance_name
  region           = var.region
  database_version = "MYSQL_8_4"

  deletion_protection = true

  settings {
    tier              = "db-f1-micro"
    edition           = "ENTERPRISE"
    availability_type = "ZONAL"

    connector_enforcement       = "REQUIRED"
    deletion_protection_enabled = true

    disk_size       = 10
    disk_type       = "PD_SSD"
    disk_autoresize = false

    backup_configuration {
      enabled                        = false
      binary_log_enabled             = false
      point_in_time_recovery_enabled = false
    }

    ip_configuration {
      ipv4_enabled = true
    }

    user_labels = local.common_labels
  }

  depends_on = [google_project_service.required["sqladmin.googleapis.com"]]
}

resource "google_sql_database" "application" {
  project  = var.project_id
  instance = google_sql_database_instance.main.name
  name     = local.database_name
  charset  = "utf8mb4"
}
