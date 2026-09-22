resource "google_storage_bucket_object" "photo_processor_source" {
  name         = local.photo_processor_source_name
  bucket       = google_storage_bucket.function_source.name
  source       = var.function_source_zip
  content_type = "application/zip"
}

resource "google_cloudfunctions2_function" "photo_processor" {
  project     = var.project_id
  name        = "photo-processor"
  location    = var.region
  description = "Processa imagens originais e publica referências das imagens processadas."
  labels      = local.common_labels

  build_config {
    runtime           = "java25"
    entry_point       = "com.example.photoprocessor.processamento.PhotoProcessorFunction"
    service_account   = google_service_account.application["photo_processor_builder"].id
    docker_repository = google_artifact_registry_repository.application.id

    source {
      storage_source {
        bucket     = google_storage_bucket_object.photo_processor_source.bucket
        object     = google_storage_bucket_object.photo_processor_source.name
        generation = google_storage_bucket_object.photo_processor_source.generation
      }
    }
  }

  service_config {
    available_cpu                    = "1"
    available_memory                 = "1Gi"
    timeout_seconds                  = 120
    min_instance_count               = 0
    max_instance_count               = 2
    max_instance_request_concurrency = 1
    service_account_email            = google_service_account.application["photo_processor"].email

    environment_variables = {
      GCP_PROJECT_ID      = var.project_id
      ORIGINAL_BUCKET     = google_storage_bucket.original.name
      PROCESSED_BUCKET    = google_storage_bucket.processed.name
      PUBSUB_RESULT_TOPIC = google_pubsub_topic.photo_processed.name
    }
  }

  depends_on = [
    google_project_service.required["cloudbuild.googleapis.com"],
    google_project_service.required["cloudfunctions.googleapis.com"],
    google_project_service.required["run.googleapis.com"],
    google_artifact_registry_repository_iam_member.photo_processor_builder_writer,
    google_project_iam_member.photo_processor_builder_log_writer,
    google_storage_bucket_iam_member.photo_processor_builder_source_viewer,
  ]
}
