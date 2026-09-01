FROM node:24-alpine

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
