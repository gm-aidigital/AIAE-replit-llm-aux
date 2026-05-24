# Agents

This repository's authoritative context for any AI coding agent
(Replit Agent, Claude Code, Cursor, Lovable, etc.) lives in:

1. `replit.md` — living entrypoint (Overview / User Preferences /
   System Architecture / External Dependencies).
2. `custom_instruction/instructions.md` — static authoritative company rules.
3. `.agents/skills/*/SKILL.md` — on-demand workflows compliant with the
   [Agent Skills specification](https://agentskills.io/specification).
4. `templates/generated-project/*` — canonical artifacts to copy into
   generated projects.

Read those before generating or modifying code. Do not duplicate their
content into chat output or generated files — reference them.
