# Testing Policy — Phased

Single source of truth for what level of testing is expected at each
stage of the project. The policy is **phased deliberately**: writing
tests before the app works costs tokens and gets thrown away when the
implementation shape changes. So we defer tests until the app actually
runs end-to-end — but once it does, tests become mandatory before
publish.

## Phases

| Phase | Trigger | Tests required? | JaCoCo gate |
|---|---|---|---|
| **1. Building** | Initial generation; app not yet running E2E | **No** — best-effort, can skip with `-DskipTests` | `0%` (gate disabled) |
| **2. Post-working** | Replit Run launches; mock-login → `/auth/me` → 200; main CRUD reaches DB and back | **Yes** — happy-path per endpoint, auth states (401/403), error contract | Soft `~50%`, never **decreases** |
| **3. Handoff** | Engineering takeover | **Strict** — full coverage including IT, edge cases, business invariants | `80%` enforced (`-Phandoff`) |

### Phase 1 — Building

Agent is iterating on the app shape, debugging compile/runtime issues,
wiring auth, getting Liquibase to run. Tests written here will be
rewritten when the implementation changes — wasted tokens.

Allowed:
- `mvn install -DskipTests -Dcheckstyle.skip=true` for fast dev loops.
- No test directories required.
- JaCoCo plugin still installed; gate at `0.00` so `verify` passes.

Not allowed:
- Removing the JaCoCo plugin from the parent POM.
- Removing `src/test/` directories that *do* exist.
- Pushing to `main` without at least one smoke test (see Phase 2).

### Phase 2 — Post-working (the moment Phase 1 ends)

The app boots, the mock-auth flow works, the demo data is visible in
the UI. At this point Agent **stops adding features** and writes the
tests. This is the "best-effort, but mandatory" phase the user asked
for: not perfect coverage, but every public surface has at least one
test, and the test suite is part of the same generation pass.

Required per service / endpoint:

1. **Success path** — for every `@Service` public method, one happy-path
   `*Test` with the AppException negative case mocked.
2. **REST contract** — for every controller method, one
   `@SpringBootTest` or `@WebMvcTest` that:
   - calls with a valid mock JWT → expects 2xx + canonical DTO shape,
   - calls without a token → expects 401 + `ApiErrorV1` body,
   - calls with insufficient role → expects 403 + `ApiErrorV1` body.
3. **Error mapping** — one test per non-trivial `AppErrorReason` to
   confirm the `GlobalExceptionHandler` translates it to the correct
   HTTP code.
4. **Liquibase smoke** — `@DataJpaTest` (or Testcontainers) that runs
   the master changelog; ensures migrations apply cleanly.

JaCoCo gate moves from `0.00` to `current_coverage` (set the property to
whatever the suite actually delivers — usually `0.40`–`0.60` at this
stage). Future commits cannot decrease it. This is the "ratchet" — the
project never loses coverage, only adds.

### Phase 3 — Engineering handoff

When the demo is accepted and engineering takes over, tighten:

- JaCoCo `0.80` line coverage (the `handoff` Maven profile flips it).
- Integration tests with Testcontainers Postgres (`*IT.java`,
  `mvn -Phandoff failsafe:integration-test`).
- Contract tests against the OpenAPI YAML (e.g. `springdoc` + REST
  Assured, or a contract test framework of the team's choice).
- Mutation testing (PITest) is nice-to-have, not required.

The `handoff` profile activates the strict gate:

```bash
mvn -Phandoff verify
```

This is the command engineering runs in their CI before accepting the
codebase.

### Phase 3-done — remove the phased plumbing

The `handoff` profile is a transition-period artifact: it lets engineering
validate "the code clears 0.80" before they accept the project, without
the MVP author having to commit to that bar prematurely. Once engineering
takes ownership and the suite reliably clears `0.80`, **collapse the
profile into the default**:

1. In `backend/pom.xml`, change the default property:
   `<jacoco.line.coverage>0.00</jacoco.line.coverage>` → `0.80`.
2. Delete the `<profiles>` block (the `handoff` profile becomes redundant
   when the default already enforces `0.80`).
3. Update the project README to drop references to `-Phandoff`.
4. Engineering CI command simplifies: `mvn verify` (no profile flag).

Why remove rather than keep: keeping a profile that's identical to the
default is dead config — it confuses future contributors ("when do I use
`-Phandoff`?"). The profile existed only while the default was `0.00`.

The phased policy (this file) is also fair game to delete from the
project's documentation **after handoff** — at that point the company's
own engineering standards take over and the MVP-phase rules no longer
apply. Engineering may keep this file for historical context, or remove
it; both are fine.

## Maven plumbing

The parent POM declares two coverage thresholds via a profile switch.
Default phase (Phase 1 / 2) uses the property `jacoco.line.coverage`
(starts at `0.00`, ratchets up in Phase 2). The `handoff` profile sets
it to `0.80`:

```xml
<properties>
    <!-- Default: gate effectively off; ratchet up manually as suite grows. -->
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

The `jacoco-check` execution reads `${jacoco.line.coverage}` and binds
to the `verify` phase — same plumbing, two thresholds.

## When Agent should switch phases

Agent moves from Phase 1 to Phase 2 the moment ALL of these are true:

- [ ] `mvn -f backend/pom.xml -DskipTests package` succeeds.
- [ ] Replit Run boots the workspace without unhandled exceptions in logs.
- [ ] `curl /api/v1/auth/me` with a mock JWT returns 200.
- [ ] At least one feature endpoint reads from the DB and returns data.
- [ ] Frontend renders without console errors against the running backend.

That's the explicit "Phase 1 done → start writing tests" handshake.
`mvp-safety-review` SKILL refuses publish until Phase 2 has been entered
(i.e. at least one real test file exists in `*/src/test/java/`).
