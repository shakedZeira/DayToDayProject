#!/bin/sh
# Entrypoint for the combined nginx + node container (Koyeb).
#
# Order of operations:
#   1. Apply DB migrations (`prisma migrate deploy`)  -> only touches Postgres.
#   2. Seed the DB (compiled `node dist/seed.js`, idempotent) -> only DB.
#   3. Start the node Express API on a FIXED INTERNAL port 4000, backgrounded.
#      PORT=4000 is forced so Koyeb's injected PORT (8080) does NOT collide
#      with nginx and so nginx always knows where the API is.
#   4. Verify the API is healthy by polling /api/health directly on
#      127.0.0.1:4000 (no nginx involved yet), bounded to 30s.
#   5. Launch nginx in the FOREGROUND (the container's main process). If node
#      ever dies later, a SIGTERM/SIGINT trap kills node so the container exits
#      and Docker/Koyeb can restart it.
#
# nginx config uses env-templated listener port: NGINX_PORT defaults to 8080
# (Koyeb's default exposed port) but can be overridden via the env var at run.

set -eu

# Fail fast on the "no DB URL" case with a clear message instead of a cryptic
# Prisma error.
if [ -z "${DATABASE_URL:-}" ]; then
  echo "[entrypoint] FATAL: DATABASE_URL is not set" >&2
  exit 1
fi

echo "[entrypoint] Applying migrations..."
# Use the hoisted prisma CLI at /app/node_modules/.bin/prisma (not `npx prisma`)
# so no registry fetch is needed at runtime on Koyeb.
(cd /app/server && /app/node_modules/.bin/prisma migrate deploy)

echo "[entrypoint] Seeding database (compiled dist/seed.js)..."
(cd /app/server && node dist/seed.js)

echo "[entrypoint] Starting node API on 127.0.0.1:4000..."
cd /app/server
PORT=4000 node dist/index.js &

API_PID=$!
echo "[entrypoint] node API started with PID $API_PID"

# Wait for the API to come up, then confirm nginx can see it. Poll instead of
# sleeping a fixed time so boot is fast when ready and bounded when not.
API_URL="http://127.0.0.1:4000/api/health"
READY=1
i=0
while [ $i -lt 30 ]; do
  if curl -fsS "$API_URL" >/dev/null 2>&1; then
    READY=0
    break
  fi
  if ! kill -0 "$API_PID" 2>/dev/null; then
    echo "[entrypoint] FATAL: node API exited early" >&2
    exit 1
  fi
  i=$((i + 1))
  sleep 1
done

if [ "$READY" -ne 0 ]; then
  echo "[entrypoint] FATAL: node API did not become healthy after 30s" >&2
  kill "$API_PID" 2>/dev/null || true
  exit 1
fi
echo "[entrypoint] node API is healthy."

# Render the nginx listener port from env (default 8080 matches Koyeb).
NGINX_PORT="${NGINX_PORT:-8080}"
export NGINX_PORT

# envsubst with a shell-format of '${NGINX_PORT}' replaces ONLY that variable,
# leaving nginx's own variables ($uri, $host, etc.) untouched.
envsubst '${NGINX_PORT}' < /etc/nginx/conf.d/default.conf > /tmp/nginx-default.conf
mv /tmp/nginx-default.conf /etc/nginx/conf.d/default.conf

# Config sanity check; abort loudly if the rendered config is invalid.
nginx -t

# Trap: if the container gets SIGTERM/SIGINT (Koyeb stop/restart), kill node so
# the whole container can shut down cleanly.
trap 'echo "[entrypoint] shutting down (node PID $API_PID)"; kill "$API_PID" 2>/dev/null || true' TERM INT

echo "[entrypoint] Launching nginx in foreground on port ${NGINX_PORT}..."
exec nginx -g "daemon off;"