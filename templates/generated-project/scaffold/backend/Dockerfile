# Dockerfile — local-dev / handoff only. Not used on Replit.
# Multi-stage: node:22 builds Vite → maven:3.9-eclipse-temurin-21 packages
# backend (copying frontend/dist into static/) → eclipse-temurin:21-jre runs.
# Java 21 must match backend/pom.xml `<java.version>` + replit.nix `pkgs.jdk21`.
# Layout: see `templates/generated-project/structure/near-production-project-structure.md`.

# ---------- Stage 1: frontend ----------
FROM node:22-bookworm-slim AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY frontend/ ./
COPY backend/application/src/main/resources/api/v1/specs/openapi.yaml \
     /workspace/backend/application/src/main/resources/api/v1/specs/openapi.yaml
RUN npm run generate:api && npm run build

# ---------- Stage 2: backend ----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
# `COPY backend/ ./backend/` already brings backend/config/ and
# backend/lombok.config — they live INSIDE backend/, not at repo root.
COPY backend/ ./backend/
COPY --from=frontend /workspace/frontend/dist/ \
     ./backend/application/src/main/resources/static/
RUN mvn -f backend/pom.xml -B -DskipTests package

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=backend /workspace/backend/application/target/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD sh -c 'curl -fsS "http://localhost:${PORT:-8080}${APP_CONTEXT_PATH:-}/actuator/health" || exit 1'
ENV SPRING_PROFILES_ACTIVE=local
ENV PORT=8080
ENTRYPOINT ["sh","-c","exec java -jar /app/app.jar"]
