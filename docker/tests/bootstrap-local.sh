#!/bin/sh
set -eu

storage_endpoint="${STORAGE_ENDPOINT:-http://localhost:4443}"
pubsub_endpoint="${PUBSUB_ENDPOINT:-http://localhost:8085}"
project="${PUBSUB_PROJECT_ID:-local-photo-platform}"

for bucket in fotos-usuarios-original fotos-usuarios-processadas; do
  curl --fail --silent "${storage_endpoint}/storage/v1/b/${bucket}" >/dev/null
done

topics=$(curl --fail --silent "${pubsub_endpoint}/v1/projects/${project}/topics")
subscriptions=$(curl --fail --silent "${pubsub_endpoint}/v1/projects/${project}/subscriptions")
for topic in foto-processada foto-processada-dlq; do
  printf '%s' "$topics" | grep -Fq "projects/${project}/topics/${topic}"
done
for subscription in photo-consumer-sub photo-consumer-dlq-sub; do
  printf '%s' "$subscriptions" | grep -Fq "projects/${project}/subscriptions/${subscription}"
done

printf '%s\n' 'Bootstrap local validado.'

