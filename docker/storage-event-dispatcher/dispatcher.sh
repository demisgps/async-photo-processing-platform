#!/bin/sh
set -eu

storage_endpoint="${STORAGE_ENDPOINT:-http://fake-gcs-server:4443}"
bucket="${ORIGINAL_BUCKET:-fotos-usuarios-original}"
functions_endpoint="${FUNCTIONS_ENDPOINT:-http://photo-processor:8080}"
poll_interval="${POLL_INTERVAL_SECONDS:-2}"
delivery_timeout="${DELIVERY_TIMEOUT_SECONDS:-5}"
state_dir="${STATE_DIR:-/state}"
watermark_file="${state_dir}/watermark"
seen_file="${state_dir}/seen"
mkdir -p "$state_dir"
touch "$watermark_file" "$seen_file"

deliver() {
  object="$1"
  generation="$2"
  updated="$3"
  content_type="$4"
  size="$5"
  crc32c="$6"
  usuario_id="$7"
  processamento_id="$8"
  event_id="local-${generation}-${processamento_id}"
  payload=$(jq -n \
    --arg bucket "$bucket" --arg name "$object" --arg generation "$generation" \
    --arg contentType "$content_type" --arg size "$size" --arg crc32c "$crc32c" \
    --arg usuarioId "$usuario_id" --arg processamentoId "$processamento_id" \
    '{bucket:$bucket,name:$name,generation:$generation,contentType:$contentType,size:$size,crc32c:$crc32c,metadata:{usuarioId:$usuarioId,processamentoId:$processamentoId}}')
  curl --fail --silent --show-error --max-time "$delivery_timeout" \
    -X POST "$functions_endpoint" \
    -H 'Content-Type: application/cloudevents+json' \
    --data "$(jq -n --arg id "$event_id" --arg time "$updated" --argjson data "$payload" '{specversion:"1.0",id:$id,source:"//fake-gcs-server/storage/v1",type:"google.cloud.storage.object.v1.finalized",subject:("objects/"+$data.name),time:$time,datacontenttype:"application/json",data:$data}')" >/dev/null &&
    printf '%s\n' "${bucket}|${object}|${generation}" >> "$seen_file"
}

while true; do
  response=$(curl --fail --silent "${storage_endpoint}/storage/v1/b/${bucket}/o" || printf '{"items":[]}')
  printf '%s' "$response" | jq -c '.items[]?' | while IFS= read -r item; do
    object=$(printf '%s' "$item" | jq -r '.name')
    generation=$(printf '%s' "$item" | jq -r '.generation // "1"')
    updated=$(printf '%s' "$item" | jq -r '.updated // (now | todateiso8601)')
    key="${bucket}|${object}|${generation}"
    grep -Fqx "$key" "$seen_file" && continue
    content_type=$(printf '%s' "$item" | jq -r '.contentType // "application/octet-stream"')
    size=$(printf '%s' "$item" | jq -r '.size // "0"')
    crc32c=$(printf '%s' "$item" | jq -r '.crc32c // ""')
    usuario_id=$(printf '%s' "$item" | jq -r '.metadata.usuarioId // ""')
    processamento_id=$(printf '%s' "$item" | jq -r '.metadata.processamentoId // ""')
    deliver "$object" "$generation" "$updated" "$content_type" "$size" "$crc32c" "$usuario_id" "$processamento_id" &
    last=$(cat "$watermark_file")
    if [ -z "$last" ] || [ "$updated" \> "$last" ]; then
      printf '%s' "$updated" > "$watermark_file"
    fi
  done
  sleep "$poll_interval"
done
