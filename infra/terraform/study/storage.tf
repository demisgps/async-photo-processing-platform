resource "google_storage_bucket" "original" {
  project       = var.project_id
  name          = local.original_bucket_name
  location      = var.region
  storage_class = "STANDARD"

  force_destroy               = true
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  labels                      = local.common_labels

  versioning {
    enabled = false
  }

  lifecycle_rule {
    action {
      type = "Delete"
    }
    condition {
      age = 3
    }
  }

  depends_on = [google_project_service.required["storage.googleapis.com"]]
}

resource "google_storage_bucket" "processed" {
  project       = var.project_id
  name          = local.processed_bucket_name
  location      = var.region
  storage_class = "STANDARD"

  force_destroy               = true
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  labels                      = local.common_labels

  versioning {
    enabled = false
  }

  lifecycle_rule {
    action {
      type = "Delete"
    }
    condition {
      age = 3
    }
  }

  depends_on = [google_project_service.required["storage.googleapis.com"]]
}

resource "google_storage_bucket" "function_source" {
  project  = var.project_id
  name     = local.function_source_bucket_name
  location = var.region

  force_destroy               = true
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  labels                      = local.common_labels

  versioning {
    enabled = false
  }

  depends_on = [google_project_service.required["storage.googleapis.com"]]
}
