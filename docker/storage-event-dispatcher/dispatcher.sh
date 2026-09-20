#!/bin/sh
set -eu

storage_endpoint="${STORAGE_ENDPOINT:-http://fake-gcs-server:4443}"
bucket="${ORIGINAL_BUCKET:-fotos-usuarios-original}"
functions_endpoint="${FUNCTIONS_ENDPOINT:-http://photo-processor:8080}"
poll_interval="${POLL_INTERVAL_SECONDS:-2}"
delivery_timeout="${DELIVERY_TIMEOUT_SECONDS:-60}"
storage_timeout="${STORAGE_TIMEOUT_SECONDS:-5}"
state_dir="${STATE_DIR:-/state}"
done_dir="${state_dir}/done"
inflight_dir="${state_dir}/inflight"
legacy_seen_file="${state_dir}/seen"
items_file="${state_dir}/items.$$"
items_tmp_file="${items_file}.tmp"

mkdir -p "$done_dir" "$inflight_dir"

key_digest() {
  printf '%s' "$1" | sha256sum | cut -d ' ' -f 1
}

clear_abandoned_claims() {
  find "$inflight_dir" -mindepth 1 -maxdepth 1 -type d -exec rm -r -- '{}' ';'
}

migrate_legacy_seen() {
  [ -f "$legacy_seen_file" ] || return 0
  while IFS= read -r key; do
    [ -n "$key" ] || continue
    digest=$(key_digest "$key")
    completed="${done_dir}/${digest}"
    [ -d "$completed" ] && continue
    temporary="${inflight_dir}/migration-${digest}-$$"
    mkdir "$temporary" 2>/dev/null || continue
    printf '%s\n' "$key" > "${temporary}/key"
    mv "$temporary" "$completed"
  done < "$legacy_seen_file"
}

shutdown() {
  trap - INT TERM
  wait || true
  clear_abandoned_claims
  rm -f "$items_file" "$items_tmp_file"
  exit 0
}

trap shutdown INT TERM
clear_abandoned_claims
migrate_legacy_seen

claim() {
  key="$1"
  digest=$(key_digest "$key")
  completed="${done_dir}/${digest}"
  pending="${inflight_dir}/${digest}"

  if [ -f "${completed}/key" ] && [ "$(cat "${completed}/key")" = "$key" ]; then
    return 1
  fi
  mkdir "$pending" 2>/dev/null || return 1

  # Recheck after the atomic claim so completion and acquisition have no race window.
  if [ -f "${completed}/key" ] && [ "$(cat "${completed}/key")" = "$key" ]; then
    rmdir "$pending"
    return 1
  fi
  if ! printf '%s\n' "$key" > "${pending}/key"; then
    printf 'event=delivery_claim_key_write_failed keyDigest=%s\n' "$digest" >&2
    if ! rm -r "$pending"; then
      printf 'event=delivery_claim_release_failed keyDigest=%s\n' "$digest" >&2
    fi
    return 1
  fi
  printf '%s\n' "$pending"
}

deliver() {
  claim_path="$1"
  object="$2"
  generation="$3"
  updated="$4"
  content_type="$5"
  size="$6"
  crc32c="$7"
  usuario_id="$8"
  processamento_id="$9"
  event_id="local-${generation}-${processamento_id}"
  digest=${claim_path##*/}
  completed="${done_dir}/${digest}"
  cleanup_delivery() {
    delivery_status=$?
    trap - 0
    if [ -d "$claim_path" ]; then
      if rm -r "$claim_path"; then
        printf 'event=delivery_claim_released keyDigest=%s exitStatus=%s\n' \
          "$digest" "$delivery_status" >&2
      else
        printf 'event=delivery_claim_release_failed keyDigest=%s\n' "$digest" >&2
      fi
    fi
    exit "$delivery_status"
  }
  trap cleanup_delivery 0
  trap 'exit 1' HUP INT TERM

  if ! payload=$(jq -n \
    --arg bucket "$bucket" --arg name "$object" --arg generation "$generation" \
    --arg contentType "$content_type" --arg size "$size" --arg crc32c "$crc32c" \
    --arg usuarioId "$usuario_id" --arg processamentoId "$processamento_id" \
    '{bucket:$bucket,name:$name,generation:$generation,contentType:$contentType,size:$size,crc32c:$crc32c,metadata:{usuarioId:$usuarioId,processamentoId:$processamentoId}}'); then
    printf 'event=cloudevent_data_generation_failed keyDigest=%s\n' "$digest" >&2
    return 1
  fi
  if ! cloud_event=$(jq -n --arg id "$event_id" --arg time "$updated" --argjson data "$payload" \
    '{specversion:"1.0",id:$id,source:"//fake-gcs-server/storage/v1",type:"google.cloud.storage.object.v1.finalized",subject:("objects/"+$data.name),time:$time,datacontenttype:"application/json",data:$data}'); then
    printf 'event=cloudevent_generation_failed keyDigest=%s\n' "$digest" >&2
    return 1
  fi
  if ! printf '%s' "$cloud_event" | jq -e \
    'type == "object" and .specversion == "1.0" and (.id | type == "string" and length > 0) and (.data | type == "object")' \
    >/dev/null; then
    printf 'event=cloudevent_validation_failed keyDigest=%s\n' "$digest" >&2
    return 1
  fi

  if curl --fail --silent --show-error --max-time "$delivery_timeout" \
    -X POST "$functions_endpoint" \
    -H 'Content-Type: application/cloudevents+json' \
    --data "$cloud_event" >/dev/null; then
    # Rename on the same persistent filesystem atomically publishes the completed marker.
    if [ -e "$completed" ] || ! mv "$claim_path" "$completed"; then
      printf 'event=delivery_promotion_failed keyDigest=%s\n' "$digest" >&2
      return 1
    fi
  else
    printf 'event=delivery_http_failed keyDigest=%s endpoint=%s\n' \
      "$digest" "$functions_endpoint" >&2
    return 1
  fi
}

while true; do
  if ! response=$(curl --fail --silent --show-error --max-time "$storage_timeout" \
    "${storage_endpoint}/storage/v1/b/${bucket}/o"); then
    printf 'event=storage_listing_failed bucket=%s endpoint=%s timeoutSeconds=%s\n' \
      "$bucket" "$storage_endpoint" "$storage_timeout" >&2
    rm -f "$items_file" "$items_tmp_file"
    sleep "$poll_interval"
    continue
  fi
  if ! printf '%s' "$response" | jq -c -s \
    'if length != 1 then error("storage listing must contain exactly one JSON document")
     elif (.[0] | type) != "object" then error("storage listing root must be an object")
     elif ((.[0] | has("items")) and ((.[0].items | type) != "array")) then error("storage listing items must be an array")
     else .[0].items[]? end' \
    > "$items_tmp_file"; then
    printf 'event=storage_listing_invalid_response bucket=%s endpoint=%s\n' \
      "$bucket" "$storage_endpoint" >&2
    rm -f "$items_file" "$items_tmp_file"
    sleep "$poll_interval"
    continue
  fi
  mv "$items_tmp_file" "$items_file"
  while IFS= read -r item; do
    if ! printf '%s' "$item" | jq -e \
      'type == "object"
       and (.name | type == "string" and length > 0 and (contains("\u0000") | not))
       and ((has("generation") | not) or .generation == null or (.generation | type == "string" and length > 0))
       and ((has("updated") | not) or .updated == null or (.updated | type == "string" and length > 0))
       and ((has("contentType") | not) or .contentType == null or (.contentType | type == "string"))
       and ((has("size") | not) or .size == null or (.size | type == "string"))
       and ((has("crc32c") | not) or .crc32c == null or (.crc32c | type == "string"))
       and ((has("metadata") | not) or .metadata == null or
         ((.metadata | type) == "object"
          and ((.metadata | has("usuarioId") | not) or .metadata.usuarioId == null or (.metadata.usuarioId | type == "string"))
          and ((.metadata | has("processamentoId") | not) or .metadata.processamentoId == null or (.metadata.processamentoId | type == "string"))))' \
      >/dev/null; then
      printf 'event=storage_item_invalid bucket=%s\n' "$bucket" >&2
      continue
    fi
    if ! object=$(printf '%s' "$item" | jq -er '.name') \
      || ! generation=$(printf '%s' "$item" | jq -er '.generation // "1"') \
      || ! updated=$(printf '%s' "$item" | jq -er '.updated // (now | todateiso8601)') \
      || ! content_type=$(printf '%s' "$item" | jq -er '.contentType // "application/octet-stream"') \
      || ! size=$(printf '%s' "$item" | jq -er '.size // "0"') \
      || ! crc32c=$(printf '%s' "$item" | jq -er '.crc32c // ""') \
      || ! usuario_id=$(printf '%s' "$item" | jq -er '.metadata.usuarioId // ""') \
      || ! processamento_id=$(printf '%s' "$item" | jq -er '.metadata.processamentoId // ""'); then
      printf 'event=storage_item_extraction_failed bucket=%s\n' "$bucket" >&2
      continue
    fi
    key="${bucket}|${object}|${generation}"
    claim_path=$(claim "$key") || continue
    deliver "$claim_path" "$object" "$generation" "$updated" "$content_type" "$size" "$crc32c" "$usuario_id" "$processamento_id" &
  done < "$items_file"
  sleep "$poll_interval"
done
