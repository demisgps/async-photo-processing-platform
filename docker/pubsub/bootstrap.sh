#!/usr/bin/env bash
set -euo pipefail

: "${PUBSUB_EMULATOR_HOST:?PUBSUB_EMULATOR_HOST deve ser informado}"
: "${GCP_PROJECT_ID:?GCP_PROJECT_ID deve ser informado}"
: "${PUBSUB_RESULT_TOPIC:?PUBSUB_RESULT_TOPIC deve ser informado}"
: "${PUBSUB_DLT_TOPIC:?PUBSUB_DLT_TOPIC deve ser informado}"
: "${PUBSUB_MAIN_SUBSCRIPTION:?PUBSUB_MAIN_SUBSCRIPTION deve ser informado}"
: "${PUBSUB_DLT_SUBSCRIPTION:?PUBSUB_DLT_SUBSCRIPTION deve ser informado}"
: "${PUBSUB_MAIN_PUSH_ENDPOINT:?PUBSUB_MAIN_PUSH_ENDPOINT deve ser informado}"
: "${PUBSUB_DLT_PUSH_ENDPOINT:?PUBSUB_DLT_PUSH_ENDPOINT deve ser informado}"

project="$GCP_PROJECT_ID"
endpoint="http://${PUBSUB_EMULATOR_HOST}"

until curl --fail --silent "${endpoint}/v1/projects/${project}/topics" >/dev/null; do
  sleep 1
done

put_idempotently() {
  local resource="$1"
  local payload="${2:-}"
  local response_file status
  response_file="$(mktemp)"

  if [[ -n "$payload" ]]; then
    status="$(curl --silent --output "$response_file" --write-out '%{http_code}' \
      -X PUT -H 'Content-Type: application/json' --data "$payload" "${endpoint}${resource}")"
  else
    status="$(curl --silent --output "$response_file" --write-out '%{http_code}' \
      -X PUT "${endpoint}${resource}")"
  fi

  if [[ "$status" != "200" && "$status" != "409" ]]; then
    cat "$response_file" >&2
    rm -f "$response_file"
    return 1
  fi
  rm -f "$response_file"
}

put_idempotently "/v1/projects/${project}/topics/${PUBSUB_RESULT_TOPIC}"
put_idempotently "/v1/projects/${project}/topics/${PUBSUB_DLT_TOPIC}"

put_idempotently "/v1/projects/${project}/subscriptions/${PUBSUB_MAIN_SUBSCRIPTION}" "{
  \"topic\": \"projects/${project}/topics/${PUBSUB_RESULT_TOPIC}\",
  \"ackDeadlineSeconds\": 60,
  \"pushConfig\": {\"pushEndpoint\": \"${PUBSUB_MAIN_PUSH_ENDPOINT}\"},
  \"retryPolicy\": {\"minimumBackoff\": \"10s\", \"maximumBackoff\": \"300s\"},
  \"deadLetterPolicy\": {
    \"deadLetterTopic\": \"projects/${project}/topics/${PUBSUB_DLT_TOPIC}\",
    \"maxDeliveryAttempts\": 8
  },
  \"messageRetentionDuration\": \"604800s\"
}"

put_idempotently "/v1/projects/${project}/subscriptions/${PUBSUB_DLT_SUBSCRIPTION}" "{
  \"topic\": \"projects/${project}/topics/${PUBSUB_DLT_TOPIC}\",
  \"pushConfig\": {\"pushEndpoint\": \"${PUBSUB_DLT_PUSH_ENDPOINT}\"},
  \"messageRetentionDuration\": \"604800s\"
}"

echo "Pub/Sub emulator bootstrap concluído para o projeto ${project}."
