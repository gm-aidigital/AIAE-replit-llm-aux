#!/usr/bin/env bash
#
# release-replit-template.sh — build a flattened, Replit-importable template artifact.
#
# Output: dist/replit-template/ (runnable scaffold at repo root; no templates/ tree).
#
# Usage:
#   bash scripts/release-replit-template.sh
#   RELEASE_DEST=/tmp/out bash scripts/release-replit-template.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCAFFOLD="${REPO_ROOT}/templates/generated-project/scaffold"
DEST="${RELEASE_DEST:-${REPO_ROOT}/dist/replit-template}"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo "==> Materialize scaffold into ${WORK}"
SCAFFOLD_ROOT="${SCAFFOLD}" \
  TEMPLATE_REPO_ROOT="${REPO_ROOT}" \
  MATERIALIZE_DEST="${WORK}" \
  bash "${SCAFFOLD}/scripts/materialize-project.sh" replitmvp

echo "==> Copy flattened artifact to ${DEST}"
rm -rf "${DEST}"
mkdir -p "${DEST}"

rsync -a \
  --exclude node_modules \
  --exclude target \
  --exclude '.git' \
  "${WORK}/" "${DEST}/"

# Concise Replit entry point. Claude rules are materialized into CLAUDE.md and
# .claude/; Replit-specific control-plane guidance remains in AGENTS.md.
cat > "${DEST}/AGENTS.md" <<'EOF'
# Generated Replit MVP

Authoritative engineering rules are in `CLAUDE.md` and `.claude/`. Replit Agent
also follows `replit.md` for environment-specific setup.

Read `AI-DEVELOPMENT-GUIDE.md` to choose between focused Claude skills and the
optional GSD lifecycle. GSD is never initialized automatically by Replit.
Read `.claude/agent_docs/project_shape_decision.md` before deciding frontend-only
vs full-stack work.

Quick start:
1. Add Clerk Auth in Replit (CLERK_PUBLISHABLE_KEY, CLERK_SECRET_KEY).
2. Set AUTH_AUTHORIZED_PARTIES to your app origins.
3. Run `bash scripts/setup-project.sh` then `bash scripts/materialize-project.sh <app-name-package>` if needed.
4. `bash scripts/local-verify.sh`
EOF

if [ -f "${REPO_ROOT}/replit.md" ]; then
  cp "${REPO_ROOT}/replit.md" "${DEST}/replit.md"
fi

echo "==> Validate release artifact"
test -f "${DEST}/backend/pom.xml"
test -f "${DEST}/scripts/verify-gates.sh"
test -f "${DEST}/frontend/nginx.conf.template"
! test -d "${DEST}/templates/generated-project/scaffold" \
  || { echo "release must not embed control-plane scaffold path"; exit 1; }

echo "==> release-replit-template: wrote ${DEST}"
