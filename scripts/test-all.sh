#!/usr/bin/env bash
set -euo pipefail
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
export TEST_JDBC_URL="${JDBC_URL:-jdbc:postgresql://localhost:5432/axiz_copilot}"
./mvnw -B -ntp verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run verify
npm --prefix frontend test
bash scripts/test-copilot.sh
