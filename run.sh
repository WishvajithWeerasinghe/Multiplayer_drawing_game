#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

for command in docker java mvn curl setsid; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing command: $command. Install it before running this launcher." >&2
    exit 1
  fi
done

server_url="http://localhost:${SERVER_PORT:-8081}"
backend_pid=""
cleanup() {
  if [[ -n "$backend_pid" ]]; then
    echo "Stopping the backend started by this launcher..."
    kill -- "-$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

backend_ready() {
  local response
  response=$(curl --noproxy '*' -fsS --max-time 2 "$server_url/api/boards") || return 1
  [[ "$response" == \[*\] ]]
}

echo "Starting PostgreSQL..."
docker compose up -d db
db_ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if docker compose exec -T db pg_isready -U draw -d drawdb >/dev/null 2>&1; then
    db_ready=true
    break
  fi
  sleep 1
done
if [[ "$db_ready" != true ]]; then
  echo "PostgreSQL did not become ready. Database logs:" >&2
  docker compose logs --tail=40 db >&2
  exit 1
fi

if backend_ready; then
  echo "Using the backend already running at $server_url."
else
  mkdir -p .run
  echo "Starting the backend at $server_url (logs: .run/backend.log)..."
  setsid mvn -f backend/pom.xml spring-boot:run >.run/backend.log 2>&1 &
  backend_pid=$!
  server_ready=false
  for ((attempt = 0; attempt < 180; attempt++)); do
    if ! kill -0 "$backend_pid" 2>/dev/null; then
      echo "Backend startup failed:" >&2
      tail -n 60 .run/backend.log >&2
      exit 1
    fi
    if backend_ready; then
      server_ready=true
      break
    fi
    sleep 1
  done
  if [[ "$server_ready" != true ]]; then
    echo "Backend startup timed out. Last log messages:" >&2
    tail -n 60 .run/backend.log >&2
    exit 1
  fi
fi

echo "Opening Collab Draw. Close the client or press Ctrl+C to finish."
bash client/run.sh "-Dexec.args=$server_url"
