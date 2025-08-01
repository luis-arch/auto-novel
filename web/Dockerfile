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

# 明确启用并准备指定版本的PNPM
RUN corepack enable && \
    corepack prepare pnpm@8.14.0 --activate

COPY . /web
WORKDIR /web

# 安装依赖并构建
RUN pnpm install --frozen-lockfile && \
    pnpm run build

FROM caddy:2.7.4
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=builder /web/dist /dist
