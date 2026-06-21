#!/usr/bin/env bash
# Install shared Claude engineering fixtures into a project.

set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_INPUT="${1:?Usage: bash scripts/install-claude-fixtures.sh <target-project-root>}"

if [ ! -d "${TARGET_INPUT}" ]; then
  echo "install-claude-fixtures: target directory does not exist: ${TARGET_INPUT}" >&2
  exit 1
fi

TARGET_ROOT="$(cd "${TARGET_INPUT}" && pwd)"
SOURCE_CLAUDE="${SOURCE_ROOT}/.claude"
TARGET_CLAUDE="${TARGET_ROOT}/.claude"

if [ "${SOURCE_ROOT}" = "${TARGET_ROOT}" ]; then
  echo "install-claude-fixtures: source and target are the same directory" >&2
  exit 1
fi

SHARED_DIRS=(
  agent_docs
  rules
  skills
)

echo "==> Install Claude fixtures into ${TARGET_ROOT}"
mkdir -p "${TARGET_CLAUDE}"

for dir in "${SHARED_DIRS[@]}"; do
  if [ -d "${SOURCE_CLAUDE}/${dir}" ]; then
    mkdir -p "${TARGET_CLAUDE}/${dir}"
    rsync -a "${SOURCE_CLAUDE}/${dir}/" "${TARGET_CLAUDE}/${dir}/"
  fi
done

mkdir -p "${TARGET_CLAUDE}/tasks"
cp "${SOURCE_CLAUDE}/tasks/README.md" "${TARGET_CLAUDE}/tasks/README.md"
cp "${SOURCE_ROOT}/CLAUDE.md" "${TARGET_ROOT}/CLAUDE.md"
cp "${SOURCE_ROOT}/GDS-WORKFLOW-README.md" "${TARGET_ROOT}/GDS-WORKFLOW-README.md"
cp "${SOURCE_CLAUDE}/agent_docs/skill-selection.md" \
  "${TARGET_ROOT}/AI-DEVELOPMENT-GUIDE.md"

test -f "${TARGET_CLAUDE}/rules/00-backend-hard-rules.md"
test -f "${TARGET_CLAUDE}/skills/verification-gate/SKILL.md"
test -f "${TARGET_ROOT}/CLAUDE.md"
test -f "${TARGET_ROOT}/AI-DEVELOPMENT-GUIDE.md"

echo "==> install-claude-fixtures: passed"
echo "    GSD remains optional; see GDS-WORKFLOW-README.md"
