# Frontend Dockerfile — local-dev / handoff only. Not used on Replit.
#
# Build context is the REPO ROOT (set in docker-compose.yml as `context: .`).
# This lets the Node stage COPY the OpenAPI spec from backend/ directly at
# build time, which `volumes:` cannot do (volumes are runtime-only).

# ---------- Stage 1: build ----------
FROM node:22-bookworm-slim AS build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY frontend/ ./
# `generate:api` reads ../backend/application/src/main/resources/api/v1/specs/openapi.yaml
# (spec lives OUTSIDE static/ so Vite's `emptyOutDir: true` can't wipe it —
# see OpenApiSpecConfig.java header). Copy spec into the expected relative
# location for the npm script.
COPY backend/application/src/main/resources/api/v1/specs/openapi.yaml \
     /workspace/backend/application/src/main/resources/api/v1/specs/openapi.yaml
RUN npm run generate:api && npm run build

# ---------- Stage 2: runtime (static SPA via nginx) ----------
FROM nginx:1.27-alpine
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
