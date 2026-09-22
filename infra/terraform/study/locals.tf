locals {
  environment = "study"
  name_prefix = "photo-platform-${local.environment}"

  original_bucket_name        = "${var.project_id}-${local.environment}-photos-original"
  processed_bucket_name       = "${var.project_id}-${local.environment}-photos-processed"
  function_source_bucket_name = "${var.project_id}-${local.environment}-function-source"
  sql_instance_name           = "${local.name_prefix}-mysql"
  database_name               = "photo_platform"
  db_password_secret_id       = "${local.name_prefix}-db-password"

  common_labels = {
    environment = local.environment
    managed_by  = "terraform"
    project     = "async-photo-processing-platform"
  }
}
