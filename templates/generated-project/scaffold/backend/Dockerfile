# Dockerfile — local-dev / handoff only. Not used on Replit.
#
# Project layout:
#   <root>/
#     backend/                  ← parent pom.xml + all Java modules
#       pom.xml                 ← Maven parent (NOT at project root)
#       application/, service/, domain/, db/, common/, external-services/
#     frontend/                 ← React + Vite
#     config/                   ← Checkstyle config (referenced from backend/pom.xml)
#     lombok.config
#     docker-compose.yml
#
# Multi-stage build:
#   1. node:22 builds the frontend (Vite) → frontend/dist/.
#   2. maven:3.9-eclipse-temurin-21 packages the backend, copying
#      frontend/dist into backend/application/src/main/resources/static/
#      so the resulting JAR serves the SPA.
#   3. eclipse-temurin:21-jre runs the JAR.
#
# Java 21 LTS — matches the parent pom.xml `<java.version>` and replit.nix
# `pkgs.jdk21`. Switch all three together when bumping to Java 25.

# ---------- Stage 1: frontend ----------
FROM node:22-bookworm-slim AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY frontend/ ./
COPY backend/application/src/main/resources/static/api/v1/specs/openapi.yaml \
     /workspace/backend/application/src/main/resources/static/api/v1/specs/openapi.yaml
RUN npm run generate:api && npm run build

# ---------- Stage 2: backend ----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY backend/ ./backend/
COPY config/ ./config/
COPY lombok.config ./
COPY --from=frontend /workspace/frontend/dist/ \
     ./backend/application/src/main/resources/static/
RUN mvn -f backend/pom.xml -B -DskipTests package

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /workspace/backend/application/target/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD curl -fsS "http://localhost:${PORT:-8080}${APP_CONTEXT_PATH:-/}actuator/health" || exit 1
ENV SPRING_PROFILES_ACTIVE=local
ENV PORT=8080
ENTRYPOINT ["sh","-c","exec java -jar /app/app.jar"]
