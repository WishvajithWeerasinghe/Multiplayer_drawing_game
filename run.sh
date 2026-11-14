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
server_port="${SERVER_PORT:-8081}"
wsl_ip=""
windows_ipv4=""
if command -v hostname >/dev/null 2>&1; then
  wsl_ip=$(hostname -I 2>/dev/null | awk '{print $1}') || wsl_ip=""
fi
if command -v powershell.exe >/dev/null 2>&1; then
  windows_ipv4=$(powershell.exe -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 -AddressState Preferred | Where-Object { \$_.IPAddress -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and \$_.InterfaceAlias -notmatch 'WSL|Default Switch|Docker' } | Select-Object -ExpandProperty IPAddress" 2>/dev/null | tr -d '\r') || windows_ipv4=""
fi

if [[ -n "$wsl_ip" || -n "$windows_ipv4" ]]; then
  if [[ -z "${ALLOWED_ORIGINS:-}" ]]; then
    ALLOWED_ORIGINS="http://localhost:${server_port},http://127.0.0.1:${server_port}"
  fi
  if [[ -n "$wsl_ip" ]]; then
    origin="http://${wsl_ip}:${server_port}"
    case ",${ALLOWED_ORIGINS}," in
      *,"$origin",*) ;;
      *) ALLOWED_ORIGINS="${ALLOWED_ORIGINS},${origin}" ;;
    esac
  fi
  while IFS= read -r host_ip; do
    [[ "$host_ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || continue
    origin="http://${host_ip}:${server_port}"
    case ",${ALLOWED_ORIGINS}," in
      *,"$origin",*) ;;
      *) ALLOWED_ORIGINS="${ALLOWED_ORIGINS},${origin}" ;;
    esac
  done <<< "$windows_ipv4"
  export ALLOWED_ORIGINS
fi

backend_pid=""
cleanup() {
  local exit_status=$?
  if [[ -n "$backend_pid" ]]; then
    echo "Stopping the backend started by this launcher..."
    kill -- "-$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
  if (( exit_status != 0 && exit_status != 130 && exit_status != 143 )) && [[ -t 0 ]]; then
    echo "Launcher exited with status $exit_status. Backend log: .run/backend.log" >&2
    read -r -p "Press Enter to close this terminal..." _
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

backend_ready() {
  local response
  response=$(curl --noproxy '*' -fsS --max-time 2 "$server_url/api/boards/health" 2>/dev/null) || return 1
  [[ "$response" == *'"status":"ok"'* && "$response" == *'"ui":"vaadin"'* && "$response" == *'"gamePort":'* ]] || return 1
  response=$(curl --noproxy '*' -fsS --max-time 3 -o /dev/null -w '%{http_code}' "$server_url/" 2>/dev/null) || return 1
  [[ "$response" == "200" ]]
}

backend_api_responds() {
  local response
  response=$(curl --noproxy '*' -fsS --max-time 2 "$server_url/api/boards" 2>/dev/null) || return 1
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

if backend_api_responds && ! backend_ready; then
  echo "An older backend is running at $server_url." >&2
  echo "Stop that backend with Ctrl+C, then run this launcher again to rebuild Vaadin." >&2
  exit 1
elif backend_ready; then
  echo "Using the backend already running at $server_url."
else
  mkdir -p .run
  echo "Starting the backend at $server_url (logs: .run/backend.log)..."
  setsid mvn -B -f backend/pom.xml clean spring-boot:run >.run/backend.log 2>&1 &
  backend_pid=$!
  server_ready=false
  for ((attempt = 0; attempt < 900; attempt++)); do
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
    echo "Backend startup timed out after 15 minutes. Last log messages:" >&2
    tail -n 60 .run/backend.log >&2
    exit 1
  fi
fi

browser_url="$server_url"
if command -v powershell.exe >/dev/null 2>&1; then
  if ! powershell.exe -NoProfile -Command "\$response = Invoke-RestMethod -Uri '$server_url/api/boards/health' -TimeoutSec 3; if (\$response.status -ne 'ok' -or \$response.ui -ne 'vaadin') { exit 1 }" >/dev/null 2>&1; then
    if [[ -n "$wsl_ip" ]]; then
      browser_url="http://${wsl_ip}:${server_port}"
    fi
  fi
  echo "Collab Draw is ready at $browser_url"
  powershell.exe -NoProfile -Command "Start-Process '$browser_url'" >/dev/null 2>&1 || true
elif command -v xdg-open >/dev/null 2>&1; then
  echo "Collab Draw is ready at $browser_url"
  xdg-open "$browser_url" >/dev/null 2>&1 &
elif command -v open >/dev/null 2>&1; then
  echo "Collab Draw is ready at $browser_url"
  open "$browser_url" >/dev/null 2>&1 &
else
  echo "Collab Draw is ready. Open this address in your browser: $browser_url"
fi

if [[ -n "$wsl_ip" && -n "$windows_ipv4" ]] && command -v powershell.exe >/dev/null 2>&1 \
    && command -v wslpath >/dev/null 2>&1; then
  health_payload=$(curl --noproxy '*' -fsS --max-time 3 "$server_url/api/boards/health" 2>/dev/null || true)
  game_port=$(printf '%s' "$health_payload" | sed -n 's/.*"gamePort":"\([0-9][0-9]*\)".*/\1/p')
  if [[ -n "$game_port" ]]; then
    host_ips=$(printf '%s\n' "$windows_ipv4" | awk 'NF { if (out != "") out = out ","; out = out $0 } END { print out }')
    setup_script=$(wslpath -w "$PWD/scripts/setup-wsl-hotspot.ps1")
    echo "Setting up hotspot access for ports $server_port and $game_port. Windows may ask for Administrator approval..."
    if powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$setup_script" \
        -WslIp "$wsl_ip" -HostIps "$host_ips" -AppPort "$server_port" -GamePort "$game_port"; then
      echo "Hotspot access is configured for this WSL address."
      while IFS= read -r host_ip; do
        [[ -n "$host_ip" ]] && echo "Hotspot URL: http://${host_ip}:${server_port}"
      done <<< "$windows_ipv4"
    else
      echo "Hotspot forwarding could not be configured. The app is still available at $browser_url." >&2
    fi
  else
    echo "Could not determine the Doodle Train port; hotspot forwarding was not configured." >&2
  fi
elif [[ -n "$wsl_ip" ]]; then
  echo "Windows hotspot addresses or WSL path tools were not found; hotspot forwarding was not configured." >&2
fi

echo "Press Ctrl+C to stop the backend started by this launcher."
if [[ -n "$backend_pid" ]]; then
  while kill -0 "$backend_pid" 2>/dev/null; do
    sleep 2
  done
  echo "The backend stopped unexpectedly. Last log messages:" >&2
  tail -n 60 .run/backend.log >&2
  exit 1
fi

echo "The backend was already running and remains available."
echo "Press Ctrl+C to close this launcher; it will not stop the existing backend."
while :; do
  sleep 2
done
