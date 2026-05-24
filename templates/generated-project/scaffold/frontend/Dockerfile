# Frontend Dockerfile — local-dev / handoff only. Not used on Replit.
#
# Build context is `frontend/` (set in docker-compose.yml).
#
# Two stages: node build → nginx static. The OpenAPI YAML the frontend's
# `npm run generate:api` reads is mounted into the build context by
# docker-compose (because the spec lives under backend/, not frontend/).

# ---------- Stage 1: build ----------
FROM node:22-bookworm-slim AS build
WORKDIR /workspace
COPY package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY . ./
# `generate:api` reads ../backend/application/src/main/resources/api/v1/specs/openapi.yaml
# (spec lives OUTSIDE static/ so Vite's `emptyOutDir: true` build can't wipe it —
# see OpenApiSpecConfig.java header). Bind-mounted at /workspace-backend by
# docker-compose; the script uses a relative path so we point at the mount.
ARG OPENAPI_SPEC=/workspace-backend/application/src/main/resources/api/v1/specs/openapi.yaml
RUN OPENAPI_SPEC="${OPENAPI_SPEC}" npm run generate:api && npm run build

# ---------- Stage 2: runtime (static SPA via nginx) ----------
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
