#!/usr/bin/env bash
# Contract test for release-replit-template.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "${OUT}"' EXIT

RELEASE_DEST="${OUT}/artifact" bash "${REPO_ROOT}/scripts/release-replit-template.sh"

test -f "${OUT}/artifact/backend/pom.xml"
test -f "${OUT}/artifact/scripts/materialize-project.sh"
test -f "${OUT}/artifact/AGENTS.md"
echo "test-release-replit-template: passed"
