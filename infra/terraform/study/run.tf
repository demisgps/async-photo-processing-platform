resource "google_cloud_run_v2_service" "photo_api" {
  project  = var.project_id
  name     = "photo-api"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"
  labels   = local.common_labels

  deletion_protection = true

  template {
    service_account                  = google_service_account.application["photo_api"].email
    timeout                          = "60s"
    max_instance_request_concurrency = 20

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = var.api_image

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }

      env {
        name  = "ORIGINAL_BUCKET"
        value = google_storage_bucket.original.name
      }

      env {
        name  = "PROCESSED_BUCKET"
        value = google_storage_bucket.processed.name
      }

      env {
        name  = "CLOUD_SQL_CONNECTION_NAME"
        value = google_sql_database_instance.main.connection_name
      }

      env {
        name  = "DB_NAME"
        value = google_sql_database.application.name
      }

      env {
        name  = "DB_USER"
        value = local.database_user
      }

      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
    }
  }

  depends_on = [
    google_project_service.required["run.googleapis.com"],
    google_secret_manager_secret_iam_member.photo_api_db_password_accessor,
  ]
}

resource "google_cloud_run_v2_service" "photo_consumer" {
  project  = var.project_id
  name     = "photo-consumer"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"
  labels   = local.common_labels

  deletion_protection = true

  template {
    service_account                  = google_service_account.application["photo_consumer"].email
    timeout                          = "60s"
    max_instance_request_concurrency = 4

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = var.consumer_image

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }

      env {
        name  = "PROCESSED_BUCKET"
        value = google_storage_bucket.processed.name
      }

      env {
        name  = "CLOUD_SQL_CONNECTION_NAME"
        value = google_sql_database_instance.main.connection_name
      }

      env {
        name  = "DB_NAME"
        value = google_sql_database.application.name
      }

      env {
        name  = "DB_USER"
        value = local.database_user
      }

      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
    }
  }

  depends_on = [
    google_project_service.required["run.googleapis.com"],
    google_secret_manager_secret_iam_member.photo_consumer_db_password_accessor,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "photo_api_public_invoker" {
  project  = var.project_id
  location = google_cloud_run_v2_service.photo_api.location
  name     = google_cloud_run_v2_service.photo_api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service_iam_member" "photo_consumer_pubsub_invoker" {
  project  = var.project_id
  location = google_cloud_run_v2_service.photo_consumer.location
  name     = google_cloud_run_v2_service.photo_consumer.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.application["pubsub_push"].email}"
}
