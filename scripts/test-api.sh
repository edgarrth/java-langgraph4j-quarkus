#!/usr/bin/env bash
set -euo pipefail
base_url="${BASE_URL:-http://localhost:8080}"
api_key="${API_KEY:-local-demo-key}"
run_id="test-$(date +%s)-$RANDOM"
request() { curl --fail-with-body --silent --show-error -H "X-Api-Key: $api_key" -H 'Content-Type: application/json' "$@"; }
for item in 'NORMAL CAPTURED' 'DECLINE REJECTED' 'FAILOVER CAPTURED' 'COMPENSATE COMPENSATED' 'REVIEW AWAITING_APPROVAL'; do
  read -r scenario expected <<<"$item"
  id="$run_id-$scenario"
  payload="$(jq -nc --arg id "$id" --arg scenario "$scenario" '{id:$id,amountMinor:15000,currency:"PEN",scenario:$scenario}')"
  response="$(request "$base_url/payments" --data "$payload")"
  jq -e --arg expected "$expected" '.state.status==$expected' <<<"$response" >/dev/null
  request "$base_url/payments" --data "$payload" | jq -e --arg expected "$expected" '.state.status==$expected' >/dev/null
  request "$base_url/payments/$id/history" | jq -e 'length>3' >/dev/null
  if [[ "$scenario" == REVIEW ]]; then
    request "$base_url/payments/$id/resume" --data '{"approval":"APPROVED"}' | jq -e '.state.status=="CAPTURED"' >/dev/null
  fi
  printf 'Verificado: %s\n' "$scenario"
done
request -N "$base_url/payments/stream" --data "$(jq -nc --arg id "$run_id-stream" '{id:$id,amountMinor:100,currency:"USD",scenario:"NORMAL"}')" | tee /tmp/axiz-stream-"$run_id".txt
grep -E 'event:result|event: result' /tmp/axiz-stream-"$run_id".txt >/dev/null
rm /tmp/axiz-stream-"$run_id".txt
printf 'Pruebas HTTP finalizadas.\n'
unauthorized=$(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/payments/$run_id-NORMAL")
[[ "$unauthorized" == 401 ]] || {
  printf "Se esperaba HTTP 401, se recibió %s\n" "$unauthorized" >&2
  exit 1
}
invalid=$(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/payments" -H "X-Api-Key: $api_key" -H 'Content-Type: application/json' --data '{"id":"invalid","amountMinor":0,"currency":"PEN","scenario":"NORMAL"}')
[[ "$invalid" == 400 ]] || {
  printf "Se esperaba HTTP 400, se recibió %s\n" "$invalid" >&2
  exit 1
}
conflict=$(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/payments" -H "X-Api-Key: $api_key" -H 'Content-Type: application/json' --data "$(jq -nc --arg id "$run_id-NORMAL" '{id:$id,amountMinor:999,currency:"PEN",scenario:"NORMAL"}')")
[[ "$conflict" == 409 ]] || {
  printf "Se esperaba HTTP 409, se recibió %s\n" "$conflict" >&2
  exit 1
}
printf 'Verificados: autenticación, validación y conflicto de idempotencia.\n'
