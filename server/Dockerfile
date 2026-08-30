FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json ./
COPY server/package.json server/package.json
COPY shared/package.json shared/package.json
RUN npm ci --workspace server --include-workspace-root

COPY . .
WORKDIR /app/server
RUN npx prisma generate
RUN npx prisma db push
RUN npm run build

EXPOSE 4000
CMD ["npm", "start"]
