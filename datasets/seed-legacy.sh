#!/usr/bin/env bash
set -euo pipefail
base_url="${BASE_URL:-http://localhost:8080}"
api_key="${API_KEY:-local-demo-key}"
dataset_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
while IFS= read -r payment; do
  curl --fail-with-body --silent --show-error "$base_url/payments" -H "X-Api-Key: $api_key" -H 'Content-Type: application/json' --data "$payment" | jq '{state, nextNode}'
done < <(jq -c '.[]' "$dataset_dir/payments.json")
printf 'Datos precargados. Rutas disponibles:\n'
printf '%s\n' "$base_url/payments" "$base_url/payments/stream" "$base_url/payments/graph" "$base_url/payments/{id}" "$base_url/payments/{id}/history" "$base_url/payments/{id}/checkpoints/{checkpointId}" "$base_url/payments/{id}/resume" "$base_url/payments/{id}/retry" "$base_url/q/health" "$base_url/q/health/live" "$base_url/q/health/ready" "$base_url/q/openapi" "$base_url/q/swagger-ui"
printf "Interfaz: http://localhost:%s\n" "${FRONTEND_PORT:-4200}"
