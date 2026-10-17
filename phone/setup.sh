#!/usr/bin/env bash
# Idempotent setup for running DayToDay on an Android phone via Termux + proot-distro Ubuntu.
set -euo pipefail

REPO_URL="https://github.com/shakedZeira/DayToDayProject.git"
APP_DIR="${APP_DIR:-$HOME/dtd}"
ENV_FILE="$APP_DIR/.env"

export DEBIAN_FRONTEND=noninteractive

echo "==> [1/11] Installing system dependencies"
apt-get update
apt-get install -y git curl ca-certificates openssl build-essential python3

echo "==> [2/11] Installing Node.js 24 via NodeSource"
curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
apt-get install -y nodejs
echo "node version: $(node -v)"

echo "==> [3/11] Fetching repository into $APP_DIR"
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" pull --ff-only
elif [ -f "$APP_DIR/package.json" ]; then
  echo "   using existing source at $APP_DIR (no git metadata)"
else
  git clone "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

echo "==> [4/11] Writing repo-root .env"
mkdir -p "$APP_DIR/data"

set_env() {
  local key="$1" value="$2"
  if [ -f "$ENV_FILE" ] && grep -q "^${key}=" "$ENV_FILE"; then
    echo "   keeping existing ${key}"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
    echo "   added ${key}"
  fi
}

# Never clobber an existing JWT_SECRET.
if [ -f "$ENV_FILE" ] && grep -q '^JWT_SECRET=' "$ENV_FILE"; then
  JWT_SECRET_VALUE="$(grep '^JWT_SECRET=' "$ENV_FILE" | head -n1 | cut -d= -f2-)"
else
  JWT_SECRET_VALUE="$(openssl rand -hex 32)"
fi

set_env DATABASE_URL "\"file:${APP_DIR}/data/dtd.db\""
set_env JWT_SECRET "$JWT_SECRET_VALUE"
set_env PORT "4000"
set_env HOST "127.0.0.1"
set_env CLIENT_URL "\"http://127.0.0.1:4000\""
set_env VISION_PROVIDER "\"stub\""
set_env LLM_PROVIDER "\"mock\""
set_env LLM_HTTP_URL "\"https://api.groq.com/openai/v1/chat/completions\""
set_env LLM_MODEL "\"llama-3.1-8b-instant\""
set_env LLM_HTTP_KEY "\"\""

echo "==> [5/11] Patching for SQLite (idempotent)"
sed -i 's/provider = "postgresql"/provider = "sqlite"/' server/prisma/schema.prisma
sed -i 's/, mode: "insensitive" as const//g' server/src/exercises.ts
sed -i 's/, mode: "insensitive" as const//g' server/src/foods.ts

echo "==> [6/11] Installing npm dependencies"
npm ci

echo "==> [7/11] Generating Prisma client"
npx prisma generate --schema server/prisma/schema.prisma

echo "==> [8/11] Pushing schema to SQLite database"
npx prisma db push --schema server/prisma/schema.prisma

echo "==> [9/11] Building server and client"
npm run build --workspace server
npm run build --workspace client

echo "==> [10/11] Seeding database"
if [ -f server/dist/seed.js ]; then
  node server/dist/seed.js
else
  npm run seed --workspace server
fi

echo "==> [11/11] Done"
cat <<'EOF'

Setup complete.

Next steps:
  1. Start the app:   bash phone/start.sh
  2. On the phone:    open Chrome -> http://127.0.0.1:4000
  3. Add to Home screen to install the PWA.

See phone/README.md for keeping it running and switching to hosted Postgres.
EOF
