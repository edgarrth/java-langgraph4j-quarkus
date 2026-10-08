#!/usr/bin/env bash
set -euo pipefail
./mvnw -B -ntp verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run verify
npm --prefix frontend test
bash scripts/test-api.sh
