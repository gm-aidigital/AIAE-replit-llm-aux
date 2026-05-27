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
if [ -f README.md ]; then
  grep -q '^## What this is' README.md \
      && grep -q '^## API' README.md \
      && grep -q 'Swagger UI' README.md \
      && grep -q 'OpenAPI YAML' README.md || {
    echo "README must describe what the app does and include API, Swagger UI, and OpenAPI YAML links"
    exit 1
  }
fi

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

if [ -d frontend/src ]; then
  ! grep -RInE 'React dev server is running|Switch the preview pane to port 5173|Open on port 5173|built React app is served directly from port 5000' \
      frontend/src >/dev/null 2>&1 || {
    echo "Frontend must render the real app, not a Replit port-switch placeholder"
    exit 1
  }
  ! grep -RInE 'Sidebar|SideNav|LeftNav|side-nav|side-menu|left-nav|left-menu|app__sidebar|layout__sidebar' \
      --include='*.ts' --include='*.tsx' --include='*.css' frontend/src >/dev/null || {
    echo "Frontend must not use a left side menu/sidebar; use top navigation, tabs, filters, and toolbars"
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
  group_id="$(sed -n 's:.*<groupId>\(.*\)</groupId>.*:\1:p' backend/pom.xml | head -n 1)"
  if [ "${group_id}" = "PACKAGE_REPLACE_ME" ] && [[ "$(pwd)" == */templates/generated-project/scaffold ]]; then
    echo "    scaffold placeholder package allowed in template source"
  else
    [[ "${group_id}" =~ ^com\.aidigital\.[a-z][a-z0-9]*$ ]] || {
      echo "backend/pom.xml groupId must be com.aidigital.<app-name-package> (lowercase alphanumeric): got ${group_id}"
      exit 1
    }
    ! grep -RInE '^package[[:space:]]+(org\.example|com\.example|io\.replit|demo|[a-z][a-z0-9_]*);' \
        backend/application/src/main/java backend/service/src/main/java backend/domain/src/main/java >/dev/null 2>&1 || {
      echo "Java packages must use com.aidigital.<app-name-package>.*, not example/replit/demo/one-segment packages"
      exit 1
    }
    ! grep -RInE '^package[[:space:]]+' \
        backend/application/src/main/java backend/service/src/main/java backend/domain/src/main/java \
        | grep -Ev '^.*:package[[:space:]]+com\.aidigital\.' >/dev/null 2>&1 || {
      echo "Every application/service/domain Java package must start with com.aidigital."
      exit 1
    }
  fi
  grep -q 'port:[[:space:]]*\${PORT:5000}' backend/application/src/main/resources/application-replit.yml || {
    echo "application-replit.yml must keep server.port: \${PORT:5000}"
    exit 1
  }
  grep -Fq 'url: jdbc:postgresql://${PGHOST' backend/application/src/main/resources/application-replit.yml || {
    echo "application-replit.yml must set spring.datasource.url from PGHOST/PGPORT/PGDATABASE"
    exit 1
  }
  grep -Fq 'username: ${PGUSER' backend/application/src/main/resources/application-replit.yml || {
    echo "application-replit.yml must set spring.datasource.username from PGUSER"
    exit 1
  }
  grep -Fq 'password: ${PGPASSWORD' backend/application/src/main/resources/application-replit.yml || {
    echo "application-replit.yml must set spring.datasource.password from PGPASSWORD"
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
  ! grep -RIn 'sslmode=require' backend/application/src/main/resources/application-replit.yml >/dev/null 2>&1 || {
    echo "application-replit.yml must not force sslmode=require for Replit Postgres"
    exit 1
  }
  ! grep -RInE 'ReplitDatabaseUrlPostProcessor|EnvironmentPostProcessor.imports|spring.factories' \
      backend/application/src/main >/dev/null 2>&1 || {
    echo "Do not use ReplitDatabaseUrlPostProcessor or EnvironmentPostProcessor registration; application-replit.yml owns datasource"
    exit 1
  }
  auth_constants="$(find backend/application/src/main/java -path '*/security/AuthConstants.java' -print -quit)"
  [ -n "${auth_constants}" ] \
      && grep -q 'CLERK_SECRET_KEY' "${auth_constants}" \
      && grep -q 'app.auth.sso.jwk-set-uri' "${auth_constants}" \
      && grep -Fq '"/"' "${auth_constants}" \
      && grep -Fq '"/assets/**"' "${auth_constants}" || {
    echo "AuthConstants must require Clerk secret plus issuer/JWKS and keep React shell/static assets public"
    exit 1
  }
  for module in $(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' backend/pom.xml); do
    module_dir="backend/${module}"
    [ -d "${module_dir}" ] || {
      echo "backend/pom.xml lists missing module: ${module}"
      exit 1
    }
    [ -d "${module_dir}/src" ] \
        && find "${module_dir}/src" -type f ! -path '*/target/*' | grep -q . || {
      echo "Maven module must not be empty/POM-only: ${module}"
      exit 1
    }
  done
  if [ ! -f backend/external-services/pom.xml ]; then
    ! grep -RIn '<artifactId>external-services</artifactId>\|<module>external-services</module>' \
        backend/pom.xml backend/service/pom.xml >/dev/null 2>&1 || {
      echo "external-services is optional; do not reference it when no module exists"
      exit 1
    }
  fi
  ! grep -RIn '@AuthenticationPrincipal' backend/application/src/main/java >/dev/null 2>&1 || {
    echo "Generated OpenAPI interfaces do not include @AuthenticationPrincipal parameters; use SecurityContextHolder/AppUserFactory"
    exit 1
  }
  ! grep -RInE '(^|[^.[:alnum:]_])getRequest[[:space:]]*\(' backend/application/src/main/java >/dev/null 2>&1 || {
    echo "Do not call getRequest() from generated OpenAPI interfaces"
    exit 1
  }
  ! grep -RIn '@Slf4j\|private static final Logger log\|Logger log' backend/application/src/main/java >/dev/null 2>&1 || {
    echo "Use explicit private static final Logger LOG in framework glue"
    exit 1
  }
  auth_props="$(find backend/application/src/main/java -path '*/security/AuthProperties.java' -print -quit)"
  [ -z "${auth_props}" ] || ! grep -RInE '@(Getter|Setter|Data|Value|RequiredArgsConstructor)' "${auth_props}" >/dev/null 2>&1 || {
    echo "AuthProperties must use explicit getters/setters, not Lombok"
    exit 1
  }
  mock_decoder="$(find backend/application/src/main/java -path '*/security/MockJwtDecoder.java' -print -quit)"
  [ -z "${mock_decoder}" ] || ! grep -q 'import io\.jsonwebtoken\.JwtException;' "${mock_decoder}" || {
    echo "MockJwtDecoder must import Spring JwtException only; catch JJWT JwtException fully-qualified"
    exit 1
  }
  logbook_config="$(find backend/application/src/main/java -path '*/config/LogbookConfig.java' -print -quit)"
  [ -z "${logbook_config}" ] || grep -Fq 'new DefaultSink(new JsonHttpLogFormatter(), new DefaultHttpLogWriter())' "${logbook_config}" || {
    echo "Logbook DefaultSink must be built with formatter + writer"
    exit 1
  }
  usage_persistence="$(find backend/application/src/main/java -path '*/observability/usage/UsageEventPersistenceService.java' -print -quit)"
  [ -n "${usage_persistence}" ] \
      && grep -Fq '@Async("usageLoggingExecutor")' "${usage_persistence}" \
      && grep -Fq '@Transactional(propagation = Propagation.REQUIRES_NEW)' "${usage_persistence}" || {
    echo "UsageEventPersistenceService must own @Async + REQUIRES_NEW persistence"
    exit 1
  }
  usage_aspect="$(find backend/application/src/main/java -path '*/observability/usage/UsageLoggingAspect.java' -print -quit)"
  [ -z "${usage_aspect}" ] || ! grep -q '^[[:space:]]*@Transactional' "${usage_aspect}" || {
    echo "Do not put @Transactional on @Around advice; use UsageEventPersistenceService"
    exit 1
  }
  usage_entity="$(find backend/domain/src/main/java -path '*/domain/usage/entities/UsageEventEntity.java' -print -quit)"
  [ -z "${usage_entity}" ] || {
    grep -Fq '@JdbcTypeCode(SqlTypes.JSON)' "${usage_entity}" \
        && grep -Fq 'Map<String, Object> attributes' "${usage_entity}" || {
      echo "UsageEventEntity.attributes must be JSONB via @JdbcTypeCode(SqlTypes.JSON) Map<String,Object>"
      exit 1
    }
  }
  ! grep -RInE "LOWER\\(CONCAT\\('%',[[:space:]]*:[A-Za-z0-9_]+" \
      backend/domain/src/main/java backend/service/src/main/java >/dev/null 2>&1 || {
    echo "Nullable JPQL LIKE params must use prebuilt patterns or cast(:param as string), not LOWER(CONCAT('%', :param, ...))"
    exit 1
  }
  ! grep -RInE 'ResponseEntity<[[:space:]]*byte\[\]|ResponseEntity<byte\[\]>' backend/application/src/main/java >/dev/null 2>&1 || {
    echo "Export/download endpoints must use OpenAPI binary schema and ResponseEntity<Resource>, not byte[]"
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

if [ -d frontend/src ]; then
  while IFS= read -r debounce_hook; do
    grep -q 'useEffect' "${debounce_hook}" \
        && grep -q 'clearTimeout' "${debounce_hook}" || {
      echo "useDebounce hooks must update via useEffect with cleanup: ${debounce_hook}"
      exit 1
    }
  done < <(find frontend/src -type f \( -name '*useDebounce*.ts' -o -name '*useDebounce*.tsx' \))
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
