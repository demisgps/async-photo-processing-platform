terraform {
  backend "gcs" {
    prefix = "billing/state"
  }
}
