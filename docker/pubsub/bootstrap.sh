#!/usr/bin/env bash
set -euo pipefail

project="${PUBSUB_PROJECT_ID:-local-photo-platform}"
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

put_idempotently "/v1/projects/${project}/topics/foto-processada"
put_idempotently "/v1/projects/${project}/topics/foto-processada-dlq"

put_idempotently "/v1/projects/${project}/subscriptions/photo-consumer-sub" "{
  \"topic\": \"projects/${project}/topics/foto-processada\",
  \"ackDeadlineSeconds\": 60,
  \"pushConfig\": {\"pushEndpoint\": \"http://photo-consumer:8081/internal/pubsub/messages\"},
  \"retryPolicy\": {\"minimumBackoff\": \"10s\", \"maximumBackoff\": \"300s\"},
  \"deadLetterPolicy\": {
    \"deadLetterTopic\": \"projects/${project}/topics/foto-processada-dlq\",
    \"maxDeliveryAttempts\": 8
  },
  \"messageRetentionDuration\": \"604800s\"
}"

put_idempotently "/v1/projects/${project}/subscriptions/photo-consumer-dlq-sub" "{
  \"topic\": \"projects/${project}/topics/foto-processada-dlq\",
  \"pushConfig\": {\"pushEndpoint\": \"http://photo-consumer:8081/internal/pubsub/dead-letter\"},
  \"messageRetentionDuration\": \"604800s\"
}"

echo "Pub/Sub emulator bootstrap concluído para o projeto ${project}."
