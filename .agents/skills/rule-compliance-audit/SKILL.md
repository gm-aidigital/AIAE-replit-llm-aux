---
name: rule-compliance-audit
description: Audit a whole generated project against the template rule set (backend testability/structure/boundary policies, canonical OpenAPI rules, canonical React frontend rules, Elevate design guidelines) and produce a categorized findings report. Use for a health check, before handoff, or when asked whether the generated project satisfies the template rules.
metadata:
  user-invocable: "true"
---

# Rule Compliance Audit

Whole-repository audit of a generated project against the template rules.
Read-only — produces a report. Load these canonical sources first:

- `templates/generated-project/testing/testing-policy.md`
- `templates/generated-project/testing/backend-test-style-rules.md`
- `templates/generated-project/structure/service-helper-extraction-policy.md`
- `templates/generated-project/structure/entity-service-boundary-policy.md`
- `templates/generated-project/openapi/canonical-openapi-rules.md`
- `templates/generated-project/frontend/canonical-react-frontend-rules.md`
- `templates/generated-project/frontend/elevate-design-guidelines.md`
- `templates/generated-project/performance/performance-engineering-rules.md`
- `templates/generated-project/observability/logbook-http-logging-rules.md`

If `scripts/local-verify.sh` / `verify-gates.sh` exist in the generated project,
run them and fold their output into the report.

## Backend scanners

```bash
grep -rnE "(^|[[:space:]])private[A-Za-z0-9_<>,.\[\] ]* [A-Za-z0-9_]+[[:space:]]*\(" backend --include=*.java \
  | grep -vE "private (final|static final|volatile)" | grep -v /target/    # no private methods
grep -rnE "^[[:space:]]+(public |protected )?(static )?(record|class|enum) [A-Z]" backend --include=*.java | grep -v /target/  # no nested data types
grep -rnE "static [A-Za-z0-9_<>]+ [a-z][A-Za-z0-9_]*[[:space:]]*\(" backend --include=*.java | grep -v "static final" | grep -v /target/  # no static bean methods
grep -rn "@Value" backend --include=*.java | grep -v /target/              # use @ConfigurationProperties
```

Read-audit: JavaDoc on every handwritten method; controllers implement generated
`*Api` and stay thin; `1 entity = 1 repository = 1 service`; service size limits;
outbound calls only in the external-services module; no edits to generated
sources; reusable external metrics only in `backend/observability`; Lombok in
every Maven child; both `ExternalClientMetricsInterceptor` and
`LogbookClientHttpRequestInterceptor` on every third-party Spring HTTP client;
no external I/O in transactions or repository/lazy access inside loops.

## Frontend scanners

```bash
grep -rnE "\b(fetch|axios|XMLHttpRequest)\b|http://localhost" frontend/src | grep -v "shared/api/client"  # shared client only
grep -rnE "tailwind|styled-components|@emotion|\.module\.css" frontend/src frontend/package.json           # forbidden styling tech
grep -rniE "sidebar|sidenav|leftnav|left-rail" frontend/src                                                # no left menu (template default)
grep -rnE "#[0-9a-fA-F]{3,6}|hsl\(|rgb\(" frontend/src --include=*.css | grep -v tokens.css                # hardcoded colors
```

Read-audit: TanStack Query for server state; Clerk-only auth with Bearer JWT
through the shared client; BEM + one block per directory; Elevate tokens and
typography; **no left sidebar** (template default — unless the project owner
explicitly overrode it); every async surface covers loading/error/empty/success.

## Output

```
STATUS: COMPLIANT | VIOLATIONS_FOUND
Backend: <n> findings   Frontend: <n> findings
Findings:
- <area> — <file>:<line> — <rule> — <fix>
Accepted exceptions:
- <e.g. nested @ConfigurationProperties group; owner-approved left nav>
```
