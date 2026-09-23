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

resource "google_pubsub_subscription" "photo_consumer" {
  project = var.project_id
  name    = "photo-consumer-sub"
  topic   = google_pubsub_topic.photo_processed.id
  labels  = local.common_labels

  ack_deadline_seconds         = 60
  message_retention_duration   = "604800s"
  enable_exactly_once_delivery = false
  enable_message_ordering      = false

  expiration_policy {
    ttl = ""
  }

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "300s"
  }

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.photo_processed_dlt.id
    max_delivery_attempts = 8
  }

  push_config {
    push_endpoint = "${google_cloud_run_v2_service.photo_consumer.uri}/internal/pubsub/messages"

    oidc_token {
      service_account_email = google_service_account.application["pubsub_push"].email
      audience              = google_cloud_run_v2_service.photo_consumer.uri
    }
  }

  depends_on = [
    google_cloud_run_v2_service_iam_member.photo_consumer_pubsub_invoker,
    google_pubsub_topic_iam_member.pubsub_service_agent_dlt_publisher,
  ]
}

resource "google_pubsub_subscription" "photo_consumer_dlt" {
  project = var.project_id
  name    = "photo-consumer-dlq-sub"
  topic   = google_pubsub_topic.photo_processed_dlt.id
  labels  = local.common_labels

  # O bootstrap local omite ackDeadlineSeconds e preserva o default Pub/Sub de 10s.
  ack_deadline_seconds         = 10
  message_retention_duration   = "604800s"
  enable_exactly_once_delivery = false
  enable_message_ordering      = false

  expiration_policy {
    ttl = ""
  }

  push_config {
    push_endpoint = "${google_cloud_run_v2_service.photo_consumer.uri}/internal/pubsub/dead-letter"

    oidc_token {
      service_account_email = google_service_account.application["pubsub_push"].email
      audience              = google_cloud_run_v2_service.photo_consumer.uri
    }
  }


  depends_on = [google_cloud_run_v2_service_iam_member.photo_consumer_pubsub_invoker]
}
