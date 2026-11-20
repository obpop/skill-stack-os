# syntax=docker/dockerfile:1.7
FROM node:22-bookworm-slim AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM node:22-bookworm-slim AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
ARG RELEASE_VERSION=local
ARG NEXT_PUBLIC_API_URL=/api/v1
ARG NEXT_PUBLIC_SITE_URL=http://localhost:3000
ARG NEXT_PUBLIC_AUTH_MODE=auth0
ARG NEXT_PUBLIC_AUTH_LOGIN_URL=/oauth2/authorization/auth0
ARG NEXT_PUBLIC_EXPANDED_UNIVERSE=false
ENV DEPLOYMENT_VERSION=${RELEASE_VERSION}
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}
ENV NEXT_PUBLIC_AUTH_MODE=${NEXT_PUBLIC_AUTH_MODE}
ENV NEXT_PUBLIC_AUTH_LOGIN_URL=${NEXT_PUBLIC_AUTH_LOGIN_URL}
ENV NEXT_PUBLIC_EXPANDED_UNIVERSE=${NEXT_PUBLIC_EXPANDED_UNIVERSE}
COPY --from=dependencies /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-bookworm-slim AS runtime
ARG RELEASE_VERSION=local
LABEL org.opencontainers.image.title="Skill Stack frontend" \
      org.opencontainers.image.revision="${RELEASE_VERSION}"
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
RUN groupadd --system --gid 10001 skillstack && useradd --system --uid 10001 --gid skillstack skillstack
COPY --from=build --chown=skillstack:skillstack /app/.next/standalone ./
COPY --from=build --chown=skillstack:skillstack /app/.next/static ./.next/static
COPY --from=build --chown=skillstack:skillstack /app/public ./public
USER 10001:10001
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=4 CMD ["node", "-e", "fetch('http://127.0.0.1:3000/').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"]
CMD ["node", "server.js"]
