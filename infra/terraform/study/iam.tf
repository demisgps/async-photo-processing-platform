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

data "google_project" "current" {
  project_id = var.project_id
}

data "google_storage_project_service_account" "current" {
  project = var.project_id

  depends_on = [google_project_service.required["storage.googleapis.com"]]
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

resource "google_storage_bucket_iam_member" "photo_processor_builder_source_viewer" {
  bucket = google_storage_bucket.function_source.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.application["photo_processor_builder"].email}"
}

resource "google_artifact_registry_repository_iam_member" "photo_processor_builder_writer" {
  project    = var.project_id
  location   = google_artifact_registry_repository.application.location
  repository = google_artifact_registry_repository.application.repository_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.application["photo_processor_builder"].email}"
}

resource "google_project_iam_member" "photo_processor_builder_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.application["photo_processor_builder"].email}"
}

resource "google_project_iam_member" "eventarc_trigger_event_receiver" {
  project = var.project_id
  role    = "roles/eventarc.eventReceiver"
  member  = "serviceAccount:${google_service_account.application["eventarc_trigger"].email}"
}

# O trigger integrado precisa da permissão antes da criação da Function. Como o
# serviço Cloud Run subjacente ainda não existe nesse ponto, o escopo suportado
# sem dependência circular é o projeto.
resource "google_project_iam_member" "eventarc_trigger_run_invoker" {
  project = var.project_id
  role    = "roles/run.invoker"
  member  = "serviceAccount:${google_service_account.application["eventarc_trigger"].email}"
}

# O Eventarc cria e administra o tópico de transporte do evento direto; por isso
# o publisher do service agent do Storage segue o escopo de projeto documentado.
resource "google_project_iam_member" "storage_service_agent_pubsub_publisher" {
  project = var.project_id
  role    = "roles/pubsub.publisher"
  member  = data.google_storage_project_service_account.current.member
}

locals {
  pubsub_service_agent_email  = "service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
  pubsub_service_agent_member = "serviceAccount:${local.pubsub_service_agent_email}"
}

resource "google_pubsub_topic_iam_member" "pubsub_service_agent_dlt_publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.photo_processed_dlt.name
  role    = "roles/pubsub.publisher"
  member  = local.pubsub_service_agent_member

  depends_on = [google_project_service.required["pubsub.googleapis.com"]]
}

resource "google_pubsub_subscription_iam_member" "pubsub_service_agent_main_subscriber" {
  project      = var.project_id
  subscription = google_pubsub_subscription.photo_consumer.name
  role         = "roles/pubsub.subscriber"
  member       = local.pubsub_service_agent_member

  depends_on = [google_project_service.required["pubsub.googleapis.com"]]
}
