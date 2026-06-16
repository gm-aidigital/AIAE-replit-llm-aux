# Testing Policy — MVP Safety Suite + Handoff Hardening

Single source of truth. MVPs do not need full production coverage, but a
zero-test project is not complete. Build/debug loops may skip tests while the
app is unstable; the final MVP must include a lean safety suite for backend
and frontend before publish or handoff. Detailed backend naming, fixture,
Instancio, captor, and spy conventions live in `backend-test-style-rules.md`.

## Testability rules (all production code)

These make every unit testable and mockable — apply to all generated code:

- **No `static` methods on beans/services.** Use instance methods so
  collaborators can be injected and mocked; `static` can't be stubbed and forces
  integration-style tests. Pure constants stay `static final`.
- **No `private` methods on beans/services.** A `private` method cannot be
  spied or stubbed, so it can only be exercised through its caller. Keep helper
  methods **package-private** (default visibility) so same-package unit tests can
  spy/stub them. When the logic is non-trivial, algorithmic, reused, or
  independently testable, **extract a `<Feature>ServiceHelper` interface +
  `<Feature>ServiceHelperImpl`** (or a `Validator`/`Policy`/`Assembler`), inject
  the interface, and unit-test it directly. See
  `structure/service-helper-extraction-policy.md`. Only `private static final`
  constants and `private final` fields stay private.
- **No nested data types.** Records, DTOs, data-holding classes, and enums are
  top-level types in a `model`/`enums` package, never nested inside another
  class. The only allowed nested types are `@ConfigurationProperties` sub-groups.
- **Every handwritten production method has JavaDoc**, regardless of visibility —
  not just public/interface methods. Generated sources, Lombok-generated
  members, and `@Override` methods that inherit their contract are exempt.
- **Mockito spies are not a design target, but service-layer tests may use them deliberately.** Do not reshape production code so a spy becomes the only way to test the class. When an existing public service method delegates to another package-private method of the same class and the project style needs that outer method isolated, create `spy(new <Feature>ServiceImpl(...))` and stub the inner call with `doReturn` / `doThrow`. The full style contract lives in `backend-test-style-rules.md`.
- Prefer constructor injection (`@RequiredArgsConstructor`) so tests pass fakes.

## Phases

| Phase | Trigger | Tests required? | JaCoCo gate |
|---|---|---|---|
| **1. Building** | Initial generation; app not yet running E2E | **No final requirement yet** — `-DskipTests` allowed only for internal debug loops | `0%` (gate disabled) |
| **2. MVP safety suite** | Replit Run launches; Clerk sign-in -> `/auth/me` -> 200; main flow reaches backend/DB and renders in frontend | **Yes** — lean backend + frontend safety tests are mandatory before completion | `80%` line / `70%` branch (parent POM defaults) |
| **3. Handoff** | Engineering takeover | **Strict** — full coverage including IT, edge cases, business invariants | `80%` enforced (`-Phandoff`) |

### Phase 1 — Building

Agent iterates app shape, debugging compile/runtime, wiring auth, Liquibase.
Tests here get rewritten on implementation churn — wasted tokens.

Allowed:
- `mvn install -DskipTests` for fast internal loops while code is still moving.
- No new test dirs required until the first runnable E2E version exists.
- JaCoCo plugin installed; gate `0.00` (verify passes).

Not allowed:
- Removing JaCoCo plugin from parent POM.
- Removing existing `src/test/` directories.
- Deleting a failing test to make the build green; fix dependencies or move
  the test to the correct module instead.
- Using `-DskipTests` in the final verification command.
- Describing the MVP as complete without the Phase 2 safety suite.

### Phase 2 — MVP safety suite

App boots, Clerk SSO works, demo data is visible, and the main frontend flow
renders against the backend. Agent **stops adding features** and writes the
minimum safety suite in the same generation pass.

Backend minimums:

1. **Application smoke** — app context starts, and health endpoint is reachable.
2. **Auth boundary** — at least one protected API returns `401` without a token
   and `2xx` with a valid Clerk JWT (use Spring Security's `jwt()` test
   post-processor; no live IdP needed), plus `401` for an invalid/expired token.
3. **Main happy path** — the primary generated flow returns the canonical DTO
   shape from controller/API level.
4. **Main error path** — validation or business error maps to the committed
   `ApiErrorV1` contract.
5. **Service behavior** — every generated `*ServiceImpl` public method has a
   focused unit test for happy path plus the main negative `AppException` path.
6. **Liquibase smoke** — when PostgreSQL/Liquibase is used, one test applies
   the master changelog.

Frontend minimums:

1. **Render smoke** — the main route renders under Vitest without crashing.
2. **Auth/session behavior** — Clerk SSO session state is represented through
   the same UI path used by the app.
3. **Async states** — the primary server-backed surface covers loading, error,
   and success states.
4. **Critical action** — forms or user actions added for the MVP have at least
   one behavior test for the expected outcome and one validation/error case.

Final MVP verification:

- `mvn -f backend/pom.xml verify`
- `cd frontend && npm test && npm run build`

Coverage may remain below production level in MVP phase. Once raised, coverage
thresholds must not be lowered to make CI pass.

### Phase 3 — Engineering handoff

Demo accepted, engineering takes over. Tighten:

- JaCoCo `0.80` line coverage (`handoff` Maven profile).
- Integration tests with Testcontainers Postgres (`*IT.java`, `mvn -Phandoff failsafe:integration-test`).
- Contract tests against OpenAPI YAML (springdoc + REST Assured or equivalent).
- Mutation testing (PITest) optional.

`mvn -Phandoff verify` — engineering's pre-acceptance CI command.

### Phase 3-done — remove the phased plumbing

`handoff` profile is transition-period. Once suite reliably clears `0.80`,
collapse into default:

1. `backend/pom.xml`: `<jacoco.line.coverage>0.00</jacoco.line.coverage>` → `0.80`.
2. Delete `<profiles>` block (redundant when default enforces `0.80`).
3. README: drop `-Phandoff` references.
4. Engineering CI: `mvn verify` (no profile flag).

Keeping a profile identical to default is dead config — confuses future contributors.

This phased-policy file is also fair game to delete after handoff —
company standards take over and MVP rules no longer apply.

## Maven plumbing

Parent POM declares two thresholds via profile. Default (Phase 1/2) uses
`jacoco.line.coverage` (starts `0.00`, then may be ratcheted after the MVP
safety suite exists). `handoff` profile sets `0.80`:

```xml
<properties>
    <jacoco.line.coverage>0.00</jacoco.line.coverage>
</properties>

<profiles>
    <profile>
        <id>handoff</id>
        <properties>
            <jacoco.line.coverage>0.80</jacoco.line.coverage>
        </properties>
    </profile>
</profiles>
```

`jacoco-check` execution reads `${jacoco.line.coverage}`, binds to `verify`.

## When Agent switches phases

Phase 1 -> 2 when ALL true:

- [ ] `mvn -f backend/pom.xml -DskipTests package` succeeds.
- [ ] Replit Run boots the workspace without unhandled exceptions in logs.
- [ ] `curl /api/v1/auth/me` with a valid Clerk JWT returns 200.
- [ ] At least one feature endpoint reads from the DB and returns data.
- [ ] Frontend renders without console errors against the running backend.
- [ ] Browser Network tab shows API calls hitting exactly
  `/api/v1/auth/me` (or `<context-path>/api/v1/auth/me`),
  never `/api/v1/api/v1/...`.

"Phase 1 done -> start writing tests" handshake. `mvp-safety-review` refuses
publish until Phase 2 is complete for both backend and frontend when those
surfaces exist.
