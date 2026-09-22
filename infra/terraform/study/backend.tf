terraform {
  backend "gcs" {
    prefix = "study/state"
  }
}
