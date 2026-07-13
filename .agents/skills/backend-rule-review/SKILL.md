---
name: backend-rule-review
description: Review changed backend Java code in a generated project against the backend rules (no static or private methods on beans, no nested data types, JavaDoc on every method, 1=1=1 entity boundaries, @ConfigurationProperties, thin controllers implementing generated *Api, enums over magic strings). Use before committing backend changes or when asked to review backend code for rule compliance.
metadata:
  user-invocable: "true"
---

# Backend Rule Review

Gate the backend diff against `templates/generated-project/testing/testing-policy.md`,
`templates/generated-project/structure/service-helper-extraction-policy.md`,
`templates/generated-project/structure/entity-service-boundary-policy.md`, and
`templates/generated-project/openapi/canonical-openapi-rules.md`, plus
`templates/generated-project/performance/performance-engineering-rules.md`.
Read-only.
Report `file:line — rule — fix`, then `STATUS: PASS | CHANGES_REQUESTED`.

Scope: working diff by default; whole backend on request. Package root is
`com.aidigital.<app-name>.*`.

## Scanners

```bash
# No static methods on beans/services (constants are fine)
grep -rnE "static [A-Za-z0-9_<>]+ [a-z][A-Za-z0-9_]*[[:space:]]*\(" backend --include=*.java | grep -v "static final" | grep -v /target/

# No private methods on beans/services
grep -rnE "(^|[[:space:]])private[A-Za-z0-9_<>,.\[\] ]* [A-Za-z0-9_]+[[:space:]]*\(" backend --include=*.java \
  | grep -vE "private (final|static final|volatile)" | grep -v /target/

# No nested data types (allow nested @ConfigurationProperties groups)
grep -rnE "^[[:space:]]+(public |protected )?(static )?(record|class|enum) [A-Z]" backend --include=*.java | grep -v /target/

# @ConfigurationProperties, not @Value
grep -rn "@Value" backend --include=*.java | grep -v /target/
```

## Read-audit checklist

- **Visibility**: helper methods are package-private (never `private`) so they
  stay spyable; only `private static final` constants / `private final` fields
  stay private. Non-trivial logic is extracted to a `Validator`/`Policy`/
  `Assembler`/`<Feature>ServiceHelper` and unit-tested.
- **Nested types**: records/DTOs/data classes/enums are top-level in
  `model`/`enums`, never nested (except `@ConfigurationProperties` sub-groups).
- **JavaDoc** on every handwritten method (generated/Lombok/inheriting
  `@Override` exempt).
- **Service size limits** from `service-helper-extraction-policy.md` (≤260 lines,
  ≤10 public, ≤8 package-private helpers, ≤8 fields).
- **Boundaries**: `1 entity = 1 repository = 1 service`; controllers implement
  generated `*Api`, stay thin, and inject no repositories; outbound calls only in
  the external-services module; reusable external-client metric helpers only in
  the attachable observability module; no edits to generated sources.
- **Performance**: no external I/O inside database transactions; no
  repository/lazy access in loops; bounded collection/summary contracts;
  set-based bulk work; no speculative pool/cache/index tuning.
- **Mandatory module/client wiring**: every Maven child declares Lombok and
  every third-party Spring HTTP client registers both
  `ExternalClientMetricsInterceptor` and `LogbookClientHttpRequestInterceptor`
  through `PooledRestClientFactory`.

## Output

```
STATUS: PASS | CHANGES_REQUESTED
Findings:
- <file>:<line> — <rule> — <fix>
Accepted exceptions:
- <e.g. nested @ConfigurationProperties group>
```
