resource "google_pubsub_topic" "photo_processed" {
  project = var.project_id
  name    = "foto-processada"
  labels  = local.common_labels

  depends_on = [google_project_service.required["pubsub.googleapis.com"]]
}

resource "google_pubsub_topic" "photo_processed_dlt" {
  project = var.project_id
  name    = "foto-processada-dlq"
  labels  = local.common_labels

  depends_on = [google_project_service.required["pubsub.googleapis.com"]]
}
