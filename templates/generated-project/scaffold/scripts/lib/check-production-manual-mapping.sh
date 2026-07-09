#!/usr/bin/env bash
#
# check-production-manual-mapping.sh — rejects service/application entity setter mapping.
#
# Looks for local `new *Entity()` followed by a setter chain on that entity
# variable. Use MapStruct `toEntity(...)` or `updateEntity(..., @MappingTarget ...)`.
#
# Usage:
#   bash scripts/lib/check-production-manual-mapping.sh [src_root...]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

shopt -s nullglob
if [ $# -eq 0 ]; then
  cd "${SCAFFOLD_ROOT}"
  roots=()
  for module in application service domain external-services; do
    [ -d "backend/${module}/src/main/java" ] && roots+=("backend/${module}/src/main/java")
  done
  set -- "${roots[@]}"
else
  cd "${PROJECT_ROOT:-$(pwd)}"
fi
if [ $# -eq 0 ]; then
  echo "check-production-manual-mapping: no source directories to scan"
  exit 0
fi

exec python3 "${SCRIPT_DIR}/scan-production-java.py" mapping "$@"
