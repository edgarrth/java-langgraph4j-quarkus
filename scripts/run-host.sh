#!/usr/bin/env bash
set -euo pipefail
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
./mvnw -B -ntp package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
