output "state_bucket_name" {
  description = "Nome do bucket GCS usado pelo backend remoto do ambiente study."
  value       = google_storage_bucket.terraform_state.name
}
