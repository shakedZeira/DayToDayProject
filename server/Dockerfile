# Debian-based (not alpine): Prisma's query/migration engines fail to load on
# node:*-alpine due to missing/libssl detection (see "Could not parse schema
# engine response" at migrate time). node:24-slim bundles OpenSSL 3, which the
# Prisma engines detect and use correctly.
FROM node:24-slim

RUN apt-get update -y \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY package.json package-lock.json ./
COPY server/package.json server/package.json
COPY shared/package.json shared/package.json
RUN npm ci --workspace server --include-workspace-root

COPY . .
WORKDIR /app/server
RUN npx prisma generate
RUN npm run build

EXPOSE 4000
CMD ["sh", "-c", "npx prisma migrate deploy && npm run seed && node dist/index.js"]
