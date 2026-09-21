#!/bin/sh
set -eu

: "${STORAGE_ENDPOINT:?STORAGE_ENDPOINT deve ser informado}"
: "${PUBSUB_ENDPOINT:?PUBSUB_ENDPOINT deve ser informado}"
: "${GCP_PROJECT_ID:?GCP_PROJECT_ID deve ser informado}"
: "${ORIGINAL_BUCKET:?ORIGINAL_BUCKET deve ser informado}"
: "${PROCESSED_BUCKET:?PROCESSED_BUCKET deve ser informado}"
: "${PUBSUB_RESULT_TOPIC:?PUBSUB_RESULT_TOPIC deve ser informado}"
: "${PUBSUB_DLT_TOPIC:?PUBSUB_DLT_TOPIC deve ser informado}"
: "${PUBSUB_MAIN_SUBSCRIPTION:?PUBSUB_MAIN_SUBSCRIPTION deve ser informado}"
: "${PUBSUB_DLT_SUBSCRIPTION:?PUBSUB_DLT_SUBSCRIPTION deve ser informado}"

storage_endpoint="$STORAGE_ENDPOINT"
pubsub_endpoint="$PUBSUB_ENDPOINT"
project="$GCP_PROJECT_ID"

for bucket in "$ORIGINAL_BUCKET" "$PROCESSED_BUCKET"; do
  curl --fail --silent "${storage_endpoint}/storage/v1/b/${bucket}" >/dev/null
done

topics=$(curl --fail --silent "${pubsub_endpoint}/v1/projects/${project}/topics")
subscriptions=$(curl --fail --silent "${pubsub_endpoint}/v1/projects/${project}/subscriptions")
for topic in "$PUBSUB_RESULT_TOPIC" "$PUBSUB_DLT_TOPIC"; do
  printf '%s' "$topics" | grep -Fq "projects/${project}/topics/${topic}"
done
for subscription in "$PUBSUB_MAIN_SUBSCRIPTION" "$PUBSUB_DLT_SUBSCRIPTION"; do
  printf '%s' "$subscriptions" | grep -Fq "projects/${project}/subscriptions/${subscription}"
done

printf '%s\n' 'Bootstrap local validado.'
