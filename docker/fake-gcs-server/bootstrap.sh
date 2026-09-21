#!/bin/sh
set -eu

: "${STORAGE_ENDPOINT:?STORAGE_ENDPOINT deve ser informado}"
: "${GCP_PROJECT_ID:?GCP_PROJECT_ID deve ser informado}"
: "${ORIGINAL_BUCKET:?ORIGINAL_BUCKET deve ser informado}"
: "${PROCESSED_BUCKET:?PROCESSED_BUCKET deve ser informado}"

endpoint="$STORAGE_ENDPOINT"

until curl --fail --silent "${endpoint}/storage/v1/b" >/dev/null; do
  sleep 1
done

for bucket in "$ORIGINAL_BUCKET" "$PROCESSED_BUCKET"; do
  if ! curl --fail --silent "${endpoint}/storage/v1/b/${bucket}" >/dev/null 2>&1; then
    curl --fail --silent --show-error \
      -X POST "${endpoint}/storage/v1/b?project=${GCP_PROJECT_ID}" \
      -H 'Content-Type: application/json' \
      --data "{\"name\":\"${bucket}\"}" >/dev/null
  fi
done
