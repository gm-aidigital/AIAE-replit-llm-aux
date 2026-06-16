# Backend Test Style Rules

Single source of truth for backend test style in generated Java backends.
Use together with `testing-policy.md`: that file defines minimum coverage and phase gates;
this file defines the shape of the tests themselves.

Production-code prerequisites these tests assume (see `testing-policy.md` and
`structure/service-helper-extraction-policy.md`): no `static` or `private`
methods on beans/services (helpers are package-private so they are spyable), no
nested data types (records/DTOs are top-level in `model`/`enums`), and JavaDoc on
every handwritten production method.

## Mandatory style

- Test method names use `should...Test()`.
- Structure each test with `// Given:`, `// When:`, and `// Then:`.
- `// When-Then:` is acceptable for direct exception assertions.
- Create the system under test and all fixtures directly inside each test method.
- Do not add common fixture factories, shared setup helpers, or reusable builder methods for unit tests.
- Use Instancio for entities, models, and DTOs instead of hand-building object graphs.
- `any()` / `anyList()` are forbidden for normal verification; use `ArgumentCaptor`, `eq`, `same`, or explicit values.
- When a public service method delegates to another package-private method of the same class and the test needs isolation, instantiate the service as `spy(new ...ServiceImpl(...))` and stub the inner call with `doReturn` / `doThrow`. (Production keeps such methods package-private, never `private`, so they stay spyable.)

## Service-layer unit tests

- Service tests are pure Mockito unit tests.
- Mock collaborators at the service boundary.
- Never bypass the architecture by mocking or calling another entity's repository directly from a higher-level service test.
- If the code under test orchestrates multiple entities, mock the paired entity services instead of their repositories.

## MVC tests

- Prefer standalone MockMvc tests.
- Assert request path, status, payload, and collaboration arguments.
- Register controller advice explicitly when error mapping is part of the contract under test.

## Repository tests

- Write `@DataJpaTest` only for non-trivial or business-critical queries.
- Seed focused SQL scenarios with `@Sql`.
- Do not spend repository tests on trivial derived queries unless the query is genuinely risky.
