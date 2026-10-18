#!/usr/bin/env bash
set -euo pipefail
curl --fail-with-body --silent --show-error "${BASE_URL:-http://localhost:8080}/api/v1/knowledge/documents" -H "X-Tenant-Id: ${TENANT_ID:-tenant-pe-01}" -H "X-Api-Key: ${API_KEY:-local-demo-key}" -H "X-Reviewer-Key: ${REVIEWER_KEY:-local-reviewer-key}" -H 'Content-Type: application/json' --data @datasets/payment-knowledge.json | jq
