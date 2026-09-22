output "artifact_registry_repository" {
  description = "Nome completo do repositório Docker da aplicação."
  value       = google_artifact_registry_repository.application.name
}

output "original_bucket_name" {
  description = "Bucket de imagens originais."
  value       = google_storage_bucket.original.name
}

output "processed_bucket_name" {
  description = "Bucket de imagens processadas."
  value       = google_storage_bucket.processed.name
}

output "function_source_bucket_name" {
  description = "Bucket reservado ao source bundle do photo-processor."
  value       = google_storage_bucket.function_source.name
}

output "cloud_sql_instance_name" {
  description = "Nome da instância Cloud SQL."
  value       = google_sql_database_instance.main.name
}

output "cloud_sql_connection_name" {
  description = "Connection name consumido pelo Cloud SQL Java Connector."
  value       = google_sql_database_instance.main.connection_name
}

output "database_name" {
  description = "Nome do schema MySQL da aplicação."
  value       = google_sql_database.application.name
}

output "db_password_secret_name" {
  description = "Nome do secret vazio que receberá DB_PASSWORD fora do Terraform."
  value       = google_secret_manager_secret.db_password.name
}

output "photo_processed_topic_name" {
  description = "Tópico principal de resultados do processamento."
  value       = google_pubsub_topic.photo_processed.name
}

output "photo_processed_dlt_topic_name" {
  description = "Dead Letter Topic dos resultados não persistidos."
  value       = google_pubsub_topic.photo_processed_dlt.name
}

output "service_account_emails" {
  description = "E-mails das service accounts criadas para as fases de runtime e deployment."
  value       = { for key, account in google_service_account.application : key => account.email }
}

output "photo_api_uri" {
  description = "URI pública do Cloud Run Service photo-api."
  value       = google_cloud_run_v2_service.photo_api.uri
}

output "photo_consumer_uri" {
  description = "URI autenticada do Cloud Run Service photo-consumer."
  value       = google_cloud_run_v2_service.photo_consumer.uri
}

output "photo_processor_function_name" {
  description = "Nome da Cloud Run Function photo-processor."
  value       = google_cloudfunctions2_function.photo_processor.name
}

output "photo_processor_uri" {
  description = "URI autenticada do serviço subjacente da photo-processor."
  value       = google_cloudfunctions2_function.photo_processor.service_config[0].uri
}

output "photo_processor_source_object" {
  description = "Objeto imutável contendo o source bundle do photo-processor."
  value       = google_storage_bucket_object.photo_processor_source.name
}

output "photo_processor_source_sha256" {
  description = "SHA-256 do source bundle usado pela Function."
  value       = local.photo_processor_source_sha
}
