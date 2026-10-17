#!/usr/bin/env bash
# Start the DayToDay API + built PWA from a single port.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

# Load repo-root .env if present.
if [ -f ./.env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

CLIENT_DIST="${CLIENT_DIST:-$PWD/client/dist}"
PORT="${PORT:-4000}"
HOST="${HOST:-127.0.0.1}"

echo "Starting DayToDay (client: $CLIENT_DIST)"
echo "Reachable at http://${HOST}:${PORT}"

if [ -f server/dist/phone.js ]; then
  exec node server/dist/phone.js
else
  echo "server/dist/phone.js not found; falling back to server/dist/index.js"
  exec node server/dist/index.js
fi
