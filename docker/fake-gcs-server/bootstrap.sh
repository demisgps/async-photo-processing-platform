#!/bin/sh
set -eu

endpoint="${STORAGE_ENDPOINT:-http://fake-gcs-server:4443}"

until curl --fail --silent "${endpoint}/storage/v1/b" >/dev/null; do
  sleep 1
done

for bucket in fotos-usuarios-original fotos-usuarios-processadas; do
  if ! curl --fail --silent "${endpoint}/storage/v1/b/${bucket}" >/dev/null 2>&1; then
    curl --fail --silent --show-error \
      -X POST "${endpoint}/storage/v1/b?project=local-photo-platform" \
      -H 'Content-Type: application/json' \
      --data "{\"name\":\"${bucket}\"}" >/dev/null
  fi
done

