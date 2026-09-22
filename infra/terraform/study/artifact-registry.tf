resource "google_artifact_registry_repository" "application" {
  project       = var.project_id
  location      = var.region
  repository_id = "photo-platform"
  description   = "Imagens Docker da photo-api e do photo-consumer."
  format        = "DOCKER"
  labels        = local.common_labels

  depends_on = [google_project_service.required["artifactregistry.googleapis.com"]]
}
