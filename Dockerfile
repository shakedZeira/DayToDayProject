# Combined single-container image: nginx (serves the static client + proxies
# /api/*) AND the node Express API. Deployed as ONE container on Koyeb.

# ---------------------------------------------------------------------------
# STAGE 1: build
# Debian-based (NOT alpine): Prisma's query/migration engines fail to load on
# node:*-alpine (missing libssl -> "Could not parse schema engine response" at
# migrate/generate time). node:24-slim bundles OpenSSL 3 which Prisma detects.
# ---------------------------------------------------------------------------
FROM node:24-slim AS build

RUN apt-get update -y \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install ALL workspaces (server, client, shared) in ONE npm ci. Two separate
# workspace-filtered `npm ci --workspace X` runs would each tear down
# node_modules and remove the other workspace's deps (observed: prisma CLI
# disappeared after the client ci re-ran).
COPY package.json package-lock.json ./
COPY server/package.json server/package.json
COPY client/package.json client/package.json
COPY shared/package.json shared/package.json
RUN npm ci

# Copy the whole repo (respects .dockerignore: no node_modules/dist/.git).
COPY . .

# Build the server (tsc compiles src -> dist; also runs prisma generate so the
# prisma client engine is produced and copied to the runtime stage). Use the
# hoisted CLI path (root node_modules/.bin) to avoid npx re-resolving from the
# registry.
WORKDIR /app/server
RUN /app/node_modules/.bin/prisma generate
RUN npm run build

# Build the client (Vite static output -> client/dist).
WORKDIR /app/client
RUN npm run build

# ---------------------------------------------------------------------------
# STAGE 2: runtime
# node:24-slim base so the Prisma CLI + engines run on Debian (needed for
# `prisma migrate deploy` and the compiled node API). nginx is installed via
# apt onto Debian rather than using nginx:alpine.
# ---------------------------------------------------------------------------
FROM node:24-slim

# gettext-base provides envsubst (used by entrypoint.sh to template the nginx
# listener port); curl is used by entrypoint.sh to poll the API for health.
RUN apt-get update -y \
  && apt-get install -y --no-install-recommends nginx gettext-base curl openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app/server

# Compiled server JS + package.json + prisma schema/migrations.
COPY --from=build /app/server/dist ./dist
COPY --from=build /app/server/package.json ./package.json
COPY --from=build /app/server/prisma ./prisma
# The FULL hoisted node_modules tree: contains the generated prisma client
# (node_modules/.prisma/client), the prisma CLI (node_modules/.bin/prisma,
# needed for `migrate deploy`), and @prisma/engines binaries. Plus the
# workspace-scoped node_modules (e.g. server/node_modules/pdf-parse is nested
# there, not hoisted). All are copied so the runtime layout matches the build.
COPY --from=build /app/node_modules /app/node_modules
COPY --from=build /app/server/node_modules ./node_modules
COPY --from=build /app/client/node_modules /app/client/node_modules
# Shared is referenced only via `import type` (no runtime require), but copy
# it anyway so any future runtime imports resolve.
COPY --from=build /app/shared /app/shared

# Static SPA build served by nginx.
COPY --from=build /app/client/dist /app/www

# Remove Debian's stock default site (sites-enabled/default) so the only
# server block in play is ours (conf.d/default.conf).
RUN rm -f /etc/nginx/sites-enabled/default

# Combined nginx config: listens on 8080, serves /app/www with SPA fallback,
# proxies /api/* to the node API on 127.0.0.1:4000.
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

# Entrypoint: migrate -> seed -> start node on :4000 -> launch nginx foreground.
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Koyeb's default exposed port. nginx listens here; the node API stays internal
# on :4000 (see entrypoint.sh).
EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
