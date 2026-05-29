# Agent entry point

This repository is a **Replit Custom Template control plane**, not a runnable app.

## Start here

1. **Rules:** `custom_instruction/instructions.md` (always authoritative).
2. **Context:** `replit.md` (project preferences and deployment model).
3. **Scaffold:** copy from `templates/generated-project/scaffold/` — never regenerate from Spring Initializr or `npm create vite`.
4. **Package naming:** run `bash scripts/apply-package-name.sh <app-name-package>` after copying the scaffold.
5. **Workflows:** load skills from `.agents/skills/*/SKILL.md` on demand — do not duplicate canonical docs inline.

## Canonical artifacts

All topic-specific rules live under `templates/generated-project/`. See the authoritative-references table in `custom_instruction/instructions.md`.
