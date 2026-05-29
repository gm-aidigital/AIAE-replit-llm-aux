#!/usr/bin/env bash
#
# ci-verify-scaffold.sh — template CI: materialize scaffold as a real project,
# apply package name, run gates + full verify (reference sample aggregate kept).
#
# Does NOT run strip-scaffold-samples.sh: after strip, domain/ is empty until the
# agent adds a real aggregate in the same commit. Publish-time strip is covered by
# local-verify.sh on generated apps and by strip-scaffold-samples.sh in template-integrity.
#
# Usage (from template repo root):
#   bash templates/generated-project/scaffold/scripts/ci-verify-scaffold.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
SCAFFOLD="${REPO_ROOT}/templates/generated-project/scaffold"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo "==> Materialize scaffold to ${WORK}"
mkdir -p "${WORK}/scripts"
cp -R "${SCAFFOLD}/backend" "${SCAFFOLD}/frontend" "${WORK}/"
cp "${SCAFFOLD}/scripts/"*.sh "${WORK}/scripts/"
chmod +x "${WORK}/scripts/"*.sh
[ -f "${SCAFFOLD}/docker-compose.yml" ] && cp "${SCAFFOLD}/docker-compose.yml" "${WORK}/"
[ -f "${SCAFFOLD}/.env.example" ] && cp "${SCAFFOLD}/.env.example" "${WORK}/"
[ -f "${SCAFFOLD}/README.md.template" ] && cp "${SCAFFOLD}/README.md.template" "${WORK}/README.md"
[ -f "${REPO_ROOT}/.replit" ] && cp "${REPO_ROOT}/.replit" "${WORK}/.replit"

cd "${WORK}"

echo "==> Apply package name"
bash scripts/apply-package-name.sh replitmvp

echo "==> structure-lint + verify-gates (reference sample allowed for compile verify)"
STRUCTURE_LINT_ALLOW_SAMPLE=1 bash scripts/structure-lint.sh
bash scripts/verify-gates.sh

echo "==> Backend mvn verify"
mvn -f backend/pom.xml -B -Dgit-commit-id.skip=true verify

echo "==> Frontend test + build"
NPM_BIN="$(pwd)/backend/application/target/frontend-toolchain/node/npm"
if [ -x "${NPM_BIN}" ]; then
  export PATH="$(dirname "${NPM_BIN}"):${PATH}"
else
  NPM_BIN="npm"
fi
( cd frontend && \
  { [ -f package-lock.json ] && "${NPM_BIN}" ci --no-audit --no-fund || "${NPM_BIN}" install --no-audit --no-fund; } && \
  "${NPM_BIN}" test && "${NPM_BIN}" run build )

echo "==> ci-verify-scaffold: passed"
