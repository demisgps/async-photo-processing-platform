terraform {
  backend "gcs" {
    prefix = "bootstrap/state"
  }
}
