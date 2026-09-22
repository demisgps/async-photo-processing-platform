locals {
  service_accounts = {
    photo_api = {
      account_id   = "sa-photo-api"
      display_name = "Photo API runtime"
      description  = "Identidade de runtime do Cloud Run photo-api."
    }
    photo_consumer = {
      account_id   = "sa-photo-consumer"
      display_name = "Photo consumer runtime"
      description  = "Identidade de runtime do Cloud Run photo-consumer."
    }
    photo_processor = {
      account_id   = "sa-photo-processor"
      display_name = "Photo processor runtime"
      description  = "Identidade de runtime da Cloud Run Function photo-processor."
    }
    photo_processor_builder = {
      account_id   = "sa-photo-processor-builder"
      display_name = "Photo processor builder"
      description  = "Identidade dedicada ao build futuro do photo-processor."
    }
    pubsub_push = {
      account_id   = "sa-pubsub-push"
      display_name = "Pub/Sub Push invoker"
      description  = "Identidade OIDC das futuras subscriptions Push."
    }
    eventarc_trigger = {
      account_id   = "sa-eventarc-trigger"
      display_name = "Eventarc trigger"
      description  = "Identidade do futuro trigger Eventarc do bucket original."
    }
    deployer = {
      account_id   = "sa-deployer"
      display_name = "Terraform deployer"
      description  = "Identidade para futura execução do Terraform por impersonation."
    }
  }
}

resource "google_service_account" "application" {
  for_each = local.service_accounts

  project      = var.project_id
  account_id   = each.value.account_id
  display_name = each.value.display_name
  description  = each.value.description

  depends_on = [google_project_service.required["iam.googleapis.com"]]
}

resource "google_project_iam_member" "photo_api_cloud_sql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.application["photo_api"].email}"
}

resource "google_project_iam_member" "photo_consumer_cloud_sql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.application["photo_consumer"].email}"
}

resource "google_storage_bucket_iam_member" "photo_api_original_object_admin" {
  bucket = google_storage_bucket.original.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.application["photo_api"].email}"
}

# A API precisa ler e excluir objetos processados na exclusão retomável.
# objectAdmin é o menor papel oficial prático que reúne esse conjunto de operações.
resource "google_storage_bucket_iam_member" "photo_api_processed_object_admin" {
  bucket = google_storage_bucket.processed.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.application["photo_api"].email}"
}

resource "google_storage_bucket_iam_member" "photo_consumer_processed_object_viewer" {
  bucket = google_storage_bucket.processed.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.application["photo_consumer"].email}"
}

resource "google_storage_bucket_iam_member" "photo_processor_original_object_viewer" {
  bucket = google_storage_bucket.original.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.application["photo_processor"].email}"
}

resource "google_storage_bucket_iam_member" "photo_processor_processed_object_admin" {
  bucket = google_storage_bucket.processed.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.application["photo_processor"].email}"
}

resource "google_secret_manager_secret_iam_member" "photo_api_db_password_accessor" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.db_password.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.application["photo_api"].email}"
}

resource "google_secret_manager_secret_iam_member" "photo_consumer_db_password_accessor" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.db_password.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.application["photo_consumer"].email}"
}

resource "google_pubsub_topic_iam_member" "photo_processor_result_publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.photo_processed.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${google_service_account.application["photo_processor"].email}"
}
