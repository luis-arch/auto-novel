#FROM node:21-slim AS builder
#COPY . /web
#WORKDIR /web
#RUN npm i -g pnpm; \
#    pnpm install --frozen-lockfile --prod; \
#    pnpm run build
#
#FROM caddy:2.7.4
#COPY Caddyfile /etc/caddy/Caddyfile
#COPY --from=builder /web/dist /dist

FROM node:21-slim AS builder

# 启用corepack确保使用正确版本的PNPM
RUN corepack enable

COPY . /web
WORKDIR /web

# 安装依赖并构建（关键修改）
RUN pnpm install --frozen-lockfile && \  # 移除--prod
    pnpm run build

# 后续阶段保持不变
FROM caddy:2.7.4
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=builder /web/dist /dist
