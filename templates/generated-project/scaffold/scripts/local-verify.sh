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
fi

if [ -f frontend/src/shared/api/client.ts ]; then
  ! grep -RIn 'baseUrl[[:space:]]*[:=][[:space:]]*["'\'']/api/v1' frontend/src/shared/api frontend/src/shared/config >/dev/null || {
    echo "Frontend apiBaseUrl/baseUrl must not be /api/v1; OpenAPI paths already include /api/v1"
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
  auth_constants="$(find backend/application/src/main/java -path '*/security/AuthConstants.java' -print -quit)"
  [ -n "${auth_constants}" ] \
      && grep -q 'CLERK_SECRET_KEY' "${auth_constants}" \
      && grep -q 'app.auth.sso.jwk-set-uri' "${auth_constants}" || {
    echo "AUTH_MODE=auto must require Clerk secret plus issuer/JWKS before enabling SSO"
    exit 1
  }
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
