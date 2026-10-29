FROM node:22.18-alpine AS build

# 可选镜像：受限网络下把 npm registry 换成可达的镜像源，留空则保持默认。
ARG NPM_REGISTRY=""

WORKDIR /app
COPY package.json package-lock.json ./
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi; npm ci
COPY . .
RUN npm run build:prod

FROM nginx:alpine

COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
