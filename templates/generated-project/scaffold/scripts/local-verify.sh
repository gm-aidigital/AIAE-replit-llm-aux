#!/usr/bin/env bash
#
# local-verify.sh — the one command engineering runs before any push.
# Mirrors the publish-gate part of mvp-safety-review. Exit non-zero on any failure.
#
# Steps:
#   1. Backend: `mvn -f backend/pom.xml verify` (Checkstyle + tests + JaCoCo)
#   2. Frontend: `npm test && npm run build` (vitest + tsc + vite)
#   3. docker-compose syntax check (does NOT run containers)

set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Replit/runtime anti-regression checks"
if [ -f .replit ]; then
  ! grep -Eq 'spring-boot:run.*backend/pom.xml|-pl application -am .*spring-boot:run' .replit || {
    echo ".replit must install the reactor first, then run spring-boot:run from backend/application/pom.xml"
    exit 1
  }
  grep -q 'mvn -f backend/application/pom.xml' .replit || {
    echo ".replit backend workflow must run backend/application/pom.xml directly"
    exit 1
  }
  grep -q 'npm run generate:api' .replit || {
    echo ".replit frontend workflow must run npm run generate:api before Vite"
    exit 1
  }
  grep -q 'PORT = "5000"' .replit || {
    echo ".replit [env] must keep PORT = \"5000\" for Replit backend"
    exit 1
  }
  grep -q 'localPort = 5000' .replit && grep -q 'externalPort = 80' .replit || {
    echo ".replit must expose exactly backend 5000 -> externalPort 80"
    exit 1
  }
  ! grep -Eq 'npm run dev.*--port 5000|vite.*--port 5000|localPort = 5173' .replit || {
    echo "Vite must not run on/expose 5000; backend owns the public Replit port"
    exit 1
  }
fi

if [ -f frontend/src/shared/api/client.ts ]; then
  ! grep -RInE '(baseUrl|apiBaseUrl|BASE_URL)[[:space:]]*[:=][[:space:]]*["'\'']/api/v1/?["'\'']' \
      frontend/src/shared/api frontend/src/shared/config >/dev/null || {
    echo "Frontend apiBaseUrl/baseUrl/BASE_URL must not be /api/v1; OpenAPI paths already include /api/v1"
    exit 1
  }
  ! grep -RInE '^VITE_API_BASE_URL=/api/v1/?$' .env.example frontend/.env* 2>/dev/null || {
    echo "VITE_API_BASE_URL must not be /api/v1; leave it empty for same-origin Replit/Vite proxy"
    exit 1
  }
  ! grep -RInE 'fetch[[:space:]]*\(|from[[:space:]]+["'\'']axios["'\'']|axios\.|new[[:space:]]+XMLHttpRequest' \
      --include='*.ts' --include='*.tsx' frontend/src >/dev/null || {
    echo "Frontend must use shared/api/client.ts (typed openapi-fetch), not raw fetch/axios/XMLHttpRequest"
    exit 1
  }
fi

if [ -f backend/application/src/main/resources/api/v1/specs/openapi.yaml ] && [ -d frontend/src ]; then
  ruby <<'RUBY'
require "yaml"

spec_path = "backend/application/src/main/resources/api/v1/specs/openapi.yaml"
spec = YAML.load_file(spec_path)
allowed = {}
(spec["paths"] || {}).each do |path, methods|
  methods.each_key do |method|
    next unless %w[get post put patch delete options head].include?(method)
    allowed[[method.upcase, path]] = true
  end
end

errors = []
Dir["frontend/src/**/*.{ts,tsx}"].each do |file|
  File.read(file).scan(/apiClient\.(GET|POST|PUT|PATCH|DELETE|OPTIONS|HEAD)\(\s*["']([^"']+)["']/) do |method, path|
    next if allowed[[method, path]]
    errors << "#{file}: apiClient.#{method}(\"#{path}\") is not defined in #{spec_path}"
  end
end

abort(errors.join("\n")) unless errors.empty?
RUBY
fi

if [ -f frontend/vite.config.ts ] && grep -q '"@/\*"' frontend/tsconfig.json 2>/dev/null; then
  grep -q 'resolve:' frontend/vite.config.ts && grep -q '"@"' frontend/vite.config.ts || {
    echo "frontend/vite.config.ts must define resolve.alias for @ when tsconfig has @/*"
    exit 1
  }
  grep -q 'CLERK_PUBLISHABLE_KEY' frontend/vite.config.ts || {
    echo "frontend/vite.config.ts must map Replit CLERK_PUBLISHABLE_KEY into VITE_CLERK_PUBLISHABLE_KEY"
    exit 1
  }
fi

if [ -d backend/application/src/main/resources ]; then
  grep -q 'port:[[:space:]]*\${PORT:5000}' backend/application/src/main/resources/application-replit.yml || {
    echo "application-replit.yml must keep server.port: \${PORT:5000}"
    exit 1
  }
  ! grep -RIn 'spring.datasource.url\|spring.datasource.username\|spring.datasource.password' \
      backend/application/src/main/resources/application-replit.yml >/dev/null 2>&1 || {
    echo "application-replit.yml must not set datasource url/username/password; DATABASE_URL post-processor owns them"
    exit 1
  }
  ! grep -RInE '^[[:space:]]*(url|username|password):' \
      backend/application/src/main/resources/application-replit.yml >/dev/null 2>&1 || {
    echo "application-replit.yml must not set datasource url/username/password; DATABASE_URL post-processor owns them"
    exit 1
  }
  ! grep -RIn '^AUTH_[A-Z_]*:[[:space:]]*\${AUTH_' backend/application/src/main/resources >/dev/null || {
    echo "Do not relay AUTH_* env vars as top-level YAML keys; Spring treats them as circular placeholders"
    exit 1
  }
  ! grep -RIn '^[[:space:]]*spring\.security\.oauth2\.resourceserver\.jwt\|^[[:space:]]*resourceserver:' backend/application/src/main/resources >/dev/null || {
    echo "Do not configure spring.security.oauth2.resourceserver.jwt.* in YAML; SecurityConfig owns JwtDecoder"
    exit 1
  }
  ! grep -RIn 'PGHOST\|PGPORT\|PGDATABASE\|PGUSER\|PGPASSWORD' backend/application/src/main/resources/application-replit.yml >/dev/null 2>&1 || {
    echo "application-replit.yml must use DATABASE_URL via ReplitDatabaseUrlPostProcessor, not PG* vars"
    exit 1
  }
  imports_file="backend/application/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports"
  [ -f "${imports_file}" ] && grep -q 'ReplitDatabaseUrlPostProcessor' "${imports_file}" || {
    echo "ReplitDatabaseUrlPostProcessor must be registered in META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports"
    exit 1
  }
  auth_constants="$(find backend/application/src/main/java -path '*/security/AuthConstants.java' -print -quit)"
  [ -n "${auth_constants}" ] \
      && grep -q 'CLERK_SECRET_KEY' "${auth_constants}" \
      && grep -q 'app.auth.sso.jwk-set-uri' "${auth_constants}" || {
    echo "AUTH_MODE=auto must require Clerk secret plus issuer/JWKS before enabling SSO"
    exit 1
  }
fi

if [ -f backend/application/pom.xml ]; then
  grep -q '<groupId>org.postgresql</groupId>' backend/application/pom.xml \
      && grep -q '<artifactId>postgresql</artifactId>' backend/application/pom.xml || {
    echo "backend/application/pom.xml must declare the PostgreSQL runtime driver"
    exit 1
  }
fi

if [ -f backend/service/pom.xml ]; then
  ! grep -Eq 'spring-boot-starter-(security|oauth2-resource-server|web)|spring-security|jakarta.servlet-api|jjwt-' \
      backend/service/pom.xml || {
    echo "backend/service/pom.xml must not depend on web/security/servlet/JWT libraries"
    exit 1
  }
  ! grep -RInE 'SecurityContextHolder|JwtDecoder|JwtAuthenticationToken|io\.jsonwebtoken|org\.springframework\.security|org\.springframework\.web|jakarta\.servlet' \
      backend/service/src/main/java >/dev/null 2>&1 || {
    echo "backend/service source must not import web/security/JWT/servlet APIs"
    exit 1
  }
fi

if [ -d backend ]; then
  find backend -path '*/src/test/java/*' \( -name '*Test.java' -o -name '*IT.java' \) | grep -q . || {
    echo "Backend tests are required; do not delete failing tests to make the build green"
    exit 1
  }
  find backend/application/src/test/java -iname '*SmokeTest.java' | grep -q . || {
    echo "Application smoke test is required"
    exit 1
  }
  if [ -d backend/db/src/main/resources/db/changelog ]; then
    find backend/application/src/test/java -iname '*Liquibase*Test.java' -o -iname '*Changelog*Test.java' | grep -q . || {
      echo "Liquibase/changelog smoke test is required when db changelogs exist"
      exit 1
    }
  fi
fi

echo "==> Backend: mvn verify"
mvn -f backend/pom.xml -B verify

if [ -f frontend/package.json ]; then
  echo "==> Frontend: npm test + build"
  NPM_BIN="$(pwd)/backend/application/target/frontend-toolchain/node/npm"
  if [ -x "${NPM_BIN}" ]; then
    export PATH="$(dirname "${NPM_BIN}"):${PATH}"
  else
    NPM_BIN="npm"
  fi
  ( cd frontend && \
    { [ -f package-lock.json ] && "${NPM_BIN}" ci --no-audit --no-fund || "${NPM_BIN}" install --no-audit --no-fund; } && \
    "${NPM_BIN}" test && "${NPM_BIN}" run build )
fi

if [ -f docker-compose.yml ]; then
  echo "==> docker compose config (syntax check, no run)"
  docker compose --profile local config >/dev/null
fi

echo "==> local-verify.sh: all checks passed"
