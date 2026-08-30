FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY client/package.json client/package.json
COPY shared/package.json shared/package.json
RUN npm ci --workspace client --include-workspace-root
COPY . .
WORKDIR /app/client
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/client/dist /usr/share/nginx/html
COPY client/nginx.conf /etc/nginx/conf.d/default.conf
