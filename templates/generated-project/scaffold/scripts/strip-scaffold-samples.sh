#!/usr/bin/env bash
#
# strip-scaffold-samples.sh — one-shot removal of the scaffold's reference
# sample aggregate. Runs as part of landing the FIRST real aggregate, after
# the agent has read the sample files for canonical layout reference.
#
# What it deletes:
#   - backend/domain/src/main/java/<base>/domain/sample/
#   - backend/service/src/main/java/<base>/service/sample/
#   - backend/service/src/test/java/<base>/service/sample/
#   - backend/db/src/main/resources/db/changelog/changes/0002-sample-reference.xml
# What it edits:
#   - backend/db/src/main/resources/db/changelog/db.changelog-master.xml
#     (removes the <include> line for 0002-sample-reference.xml and its
#     surrounding SCAFFOLD-EXAMPLE comment block)
#
# Idempotent — safe to re-run. Exits 0 even if nothing was left to remove.
#
# Usage (from project root):
#   bash scripts/strip-scaffold-samples.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

SAMPLE_CHANGELOG="backend/db/src/main/resources/db/changelog/changes/0002-sample-reference.xml"
MASTER_CHANGELOG="backend/db/src/main/resources/db/changelog/db.changelog-master.xml"

removed_any=false

remove_sample_dirs() {
    local kind="$1"   # domain or service
    local scope="$2"  # main or test
    local base="backend/${kind}/src/${scope}/java"
    while IFS= read -r path; do
        rm -rf "${path}"
        echo "    removed ${path}"
        removed_any=true
    done < <(find "${base}" -type d -path "*/${kind}/sample" 2>/dev/null)
}

echo "==> Removing scaffold sample aggregate"
remove_sample_dirs "domain" "main"
remove_sample_dirs "service" "main"
remove_sample_dirs "service" "test"

if [ -f "${SAMPLE_CHANGELOG}" ]; then
    rm -f "${SAMPLE_CHANGELOG}"
    echo "    removed ${SAMPLE_CHANGELOG}"
    removed_any=true
fi

if [ -f "${MASTER_CHANGELOG}" ] && grep -q '0002-sample-reference.xml' "${MASTER_CHANGELOG}"; then
    echo "==> Stripping sample-reference <include> from db.changelog-master.xml"
    # Delete the SCAFFOLD-EXAMPLE comment block AND the include line.
    # Portable sed (BSD + GNU): use a temp file.
    python3 - "${MASTER_CHANGELOG}" <<'PY'
import sys, re, pathlib
path = pathlib.Path(sys.argv[1])
text = path.read_text()
# Remove the SCAFFOLD EXAMPLE comment block (multi-line) immediately above
# the 0002-sample-reference include, plus the include line itself.
pattern = re.compile(
    r'\n[ \t]*<!--\s*\n[ \t]*SCAFFOLD EXAMPLE include[\s\S]*?-->\n',
    re.MULTILINE)
text = pattern.sub('\n', text)
pattern2 = re.compile(
    r'[ \t]*<include file="db/changelog/changes/0002-sample-reference\.xml"/>\n')
text = pattern2.sub('', text)
path.write_text(text)
PY
    echo "    edited ${MASTER_CHANGELOG}"
    removed_any=true
fi

if $removed_any; then
    echo "==> Done. Sample aggregate stripped."
else
    echo "==> Nothing to remove — sample aggregate already stripped."
fi
