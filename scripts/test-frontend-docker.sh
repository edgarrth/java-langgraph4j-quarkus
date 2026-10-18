#!/usr/bin/env bash
set -euo pipefail
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e npm_config_cache=/tmp/npm-cache -v "$PWD/frontend:/app" -w /app node:24-alpine sh -ec 'node --version && npm ci && npm run lint && npm run verify && npm test'
