locals {
  environment = "study"

  common_labels = {
    environment = local.environment
    managed_by  = "terraform"
    project     = "async-photo-processing-platform"
  }
}
