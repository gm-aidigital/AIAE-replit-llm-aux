#!/usr/bin/env bash
#
# setup-project.sh — runs ONCE, as the very first action after template fork.
# Cleans up Replit's auto-injected Python files, installs the canonical
# .gitignore (which excludes the template control plane from git), and
# untracks any template files git may have already grabbed.
#
# Agent runs this before writing any application code.
# Idempotent: safe to run repeatedly.
#
# Usage:
#   bash templates/generated-project/scaffold/scripts/setup-project.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
SCAFFOLD="${ROOT}/templates/generated-project/scaffold"

echo "==> Installing canonical .gitignore at project root"
cp "${SCAFFOLD}/.gitignore" "${ROOT}/.gitignore"

echo "==> Installing runtime scripts at project root (scripts/)"
# Replit deployment + the company-repo handoff need build/run/verify scripts
# at a path that survives the template control-plane cleanup. The template
# copy under scaffold/scripts/ stays as the canonical source; this step
# installs the runtime copies at <root>/scripts/ so .replit and engineers
# cloning the company repo find them at the same path.
mkdir -p "${ROOT}/scripts"
for s in replit-build.sh replit-run.sh local-verify.sh structure-lint.sh verify-gates.sh ci-verify-scaffold.sh apply-package-name.sh strip-scaffold-samples.sh; do
  cp "${SCAFFOLD}/scripts/${s}" "${ROOT}/scripts/${s}"
  chmod +x "${ROOT}/scripts/${s}"
done

echo "==> Installing project README from template (only if none exists yet)"
# Guarantees every generated project ships a README.md — the engineering
# handoff and generated-project CI both require it. The Agent fills in the
# real app name / API links when the first real feature lands.
[ -f "${ROOT}/README.md" ] || cp "${SCAFFOLD}/README.md.template" "${ROOT}/README.md"

echo "==> Removing Replit-injected Python scaffolding (Java template, not Python)"
for f in main.py pyproject.toml uv.lock poetry.lock requirements.txt Pipfile Pipfile.lock; do
  [ -f "${ROOT}/${f}" ] && rm -f "${ROOT}/${f}" && echo "    removed ${f}"
done
[ -d "${ROOT}/__pycache__" ] && rm -rf "${ROOT}/__pycache__"
[ -d "${ROOT}/.venv" ]      && rm -rf "${ROOT}/.venv"

echo "==> Untracking template control-plane from git index (if tracked)"
cd "${ROOT}"
if [ -d .git ]; then
  for path in .agents templates custom_instruction AGENTS.md replit.md \
              main.py pyproject.toml uv.lock; do
    git ls-files --error-unmatch "${path}" >/dev/null 2>&1 && \
      git rm -r --cached "${path}" >/dev/null 2>&1 && \
      echo "    untracked ${path}"
  done
fi

echo "==> Removing python-* module and flask integration from .replit (if present)"
if [ -f .replit ]; then
  # Strip python-* entries from modules array. Conservative: only the python-3.11
  # default Replit injects.
  sed -i.bak -E 's/"python-[0-9.]+"[[:space:]]*,?[[:space:]]*//g; s/,([[:space:]]*\])/\1/g' .replit
  # Remove flask/django/fastapi auto-suggested integrations.
  sed -i.bak -E '/integrations.*=.*\[.*"(flask|django|fastapi)_/d' .replit
  rm -f .replit.bak
fi

echo "==> Done."
echo "    Next: run bash scripts/apply-package-name.sh <app-name-package>, then scaffold"
echo "    the Java + Spring Boot + React app per templates/generated-project/scaffold/."
