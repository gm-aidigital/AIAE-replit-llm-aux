#!/usr/bin/env bash
# Contract test for release-replit-template.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "${OUT}"' EXIT

RELEASE_DEST="${OUT}/artifact" bash "${REPO_ROOT}/scripts/release-replit-template.sh"

test -f "${OUT}/artifact/backend/pom.xml"
test -f "${OUT}/artifact/scripts/materialize-project.sh"
test -f "${OUT}/artifact/scripts/replit-env.sh"
test -f "${OUT}/artifact/AGENTS.md"
test -f "${OUT}/artifact/CLAUDE.md"
test -f "${OUT}/artifact/AI-DEVELOPMENT-GUIDE.md"
test -f "${OUT}/artifact/.claude/agent_docs/index.md"
test -f "${OUT}/artifact/.claude/agent_docs/project_shape_decision.md"
test -f "${OUT}/artifact/.claude/agent_docs/html_only_project_migration.md"
test -f "${OUT}/artifact/.claude/rules/40-frontend-rules.md"
test -f "${OUT}/artifact/.claude/skills/task-workflow/SKILL.md"
test -f "${OUT}/artifact/.claude/skills/verification-gate/SKILL.md"
test -f "${OUT}/artifact/.claude/agent_docs/skill-selection.md"
echo "test-release-replit-template: passed"
