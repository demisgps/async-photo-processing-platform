#!/usr/bin/env bash
set -euo pipefail

project="${PUBSUB_PROJECT_ID:-local-photo-platform}"
endpoint="http://${PUBSUB_EMULATOR_HOST}"

until curl --fail --silent "${endpoint}/v1/projects/${project}/topics" >/dev/null; do
  sleep 1
done

create_topic() {
  gcloud pubsub topics describe "$1" --project="$project" >/dev/null 2>&1 ||
    gcloud pubsub topics create "$1" --project="$project" >/dev/null
}

create_topic foto-processada
create_topic foto-processada-dlq

gcloud pubsub subscriptions describe photo-consumer-sub --project="$project" >/dev/null 2>&1 ||
  gcloud pubsub subscriptions create photo-consumer-sub \
    --project="$project" \
    --topic=foto-processada \
    --ack-deadline=60 \
    --min-retry-delay=10s \
    --max-retry-delay=300s \
    --dead-letter-topic=foto-processada-dlq \
    --max-delivery-attempts=8 \
    --message-retention-duration=7d >/dev/null

gcloud pubsub subscriptions describe photo-consumer-dlq-sub --project="$project" >/dev/null 2>&1 ||
  gcloud pubsub subscriptions create photo-consumer-dlq-sub \
    --project="$project" \
    --topic=foto-processada-dlq \
    --message-retention-duration=7d >/dev/null

