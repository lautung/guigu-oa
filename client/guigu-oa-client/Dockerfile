FROM node:22.18-alpine AS build

# 可选镜像：受限网络下把 npm registry 换成可达的镜像源，留空则保持默认。
ARG NPM_REGISTRY=""

WORKDIR /app
COPY package.json package-lock.json ./
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi; npm ci
COPY . .
RUN npm run build:prod

FROM nginx:alpine

# 官方镜像启动时把 templates/*.template 经 envsubst 展开到 conf.d；
# 只会替换同名环境变量，nginx 自身的 $host/$uri 等变量保持不变。
# 默认空值让 `connect-src 'self'` 合法；只要有浏览器直连对象存储的上传，就必须设置
# CSP_CONNECT_SRC，否则预签名 PUT 会被 CSP 拦下（SEC-HDR-04 会失败）。
ENV CSP_CONNECT_SRC=""

COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
