# Canonical code patterns

Full code examples + extended "Why" rationale referenced from the main
`SKILL.md`. The SKILL keeps the rules; this file holds the worked
examples Agent copies and adapts.

## AppUser pattern (instead of pulling spring-security into service)

When a service method needs the caller's identity, controllers extract it
from `@AuthenticationPrincipal Jwt jwt` and pass an `AppUser` value object.
The service stays free of HTTP / JWT types.

```java
// application/.../<domain>/<Domain>Controller.java
@PostMapping("/{id}")
@Transactional
public ResponseEntity<<Domain>V1> update<Domain>(
        @PathVariable Long id,
        @RequestBody @Valid Update<Domain>RequestV1 req,
        @AuthenticationPrincipal Jwt jwt) {                          // ← spring-security HERE
    AppUser caller = new AppUser(jwt.getSubject(),
                                 jwt.getClaimAsString("email"),
                                 jwt.getClaimAsStringList("roles"));
    <Domain>Record record = service.update(id, mapper.toRecord(req), caller);
    return ResponseEntity.ok(mapper.toDto(record));
}
```

`AppUser` lives at `service/<base>/service/common/security/AppUser.java`
(alongside the `common/error/` and `common/observability/` subpackages)
as a plain Java `record` — no Spring imports. Application sees it
transitively (application depends on service); service classes that
receive it as a parameter import from the same module. The boundary
stays clean and service tests don't need `@WithMockUser`.

## Service that needs DB + external call

`external-services` is optional. Add it only when the project has a real
outbound integration and commit the first real client/adapter source files in
the same change. Once it exists, `service` is the only module that can talk to
both `domain` and `external-services`. External clients NEVER inject
repositories themselves — they return data; service writes via the repository.

```java
@Service
@RequiredArgsConstructor
public class <Domain>ServiceImpl implements <Domain>Service {
    private final <Domain>Repository repo;          // domain module
    private final <External>Client externalClient;  // external-services module
    private final <Domain>Mapper mapper;

    @Override
    @LogUsage(action = "<domain>.sync")
    public <Domain>Record syncFromExternal(Long id) {
        <Domain>Entity entity = repo.findById(id)
            .orElseThrow(() -> new AppException(ErrorReason.C001, id));
        <External>Data fresh = externalClient.fetch(entity.getExternalId());
        entity.applyFresh(fresh);
        return mapper.toRecord(repo.save(entity));
    }
}
```

## Thin controller — canonical and anti-pattern

The four things a controller is allowed to do: receive call, extract user,
call ONE service method, MapStruct → DTO → return.

```java
// CANONICAL
@RestController
@RequiredArgsConstructor
class <Domain>Controller implements <Domain>Api {
    private final <Domain>Service service;
    private final <Domain>ApiMapper mapper;

    @Override
    public ResponseEntity<<Domain>V1> get<Domain>(Long id) {
        var caller = SecurityContextHolder.getContext().getAuthentication();
        <Domain>Record record = service.findById(id, AppUser.from(caller));
        return ResponseEntity.ok(mapper.toDto(record));
    }
}

// ANTI-PATTERN — DO NOT do any of this
@RestController
class <Domain>Controller implements <Domain>Api {
    public ResponseEntity<<Domain>V1> get<Domain>(Long id) {
        var entity = repo.findById(id).orElseThrow(...);   // ✗ repository in controller
        if (entity.getOwnerId() != currentUser.getId() &&  // ✗ business logic
            !currentUser.isAdmin()) {
            throw new ResponseStatusException(FORBIDDEN);  // ✗ wrong exception type
        }
        var dto = new <Domain>V1();                        // ✗ manual DTO build
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        return ResponseEntity.ok(dto);
    }
}
```

The service does the business work (department check, status validation,
etc.) and throws `AppException` on any failure. The mapper does the shape
translation only.

## Service interface + impl — full canonical shape

```
backend/service/src/main/java/<base>/service/<domain>/
  services/<X>Service.java            ← public interface, what callers inject
  services/impl/<X>ServiceImpl.java   ← @Service implementation
  mappers/<X>Mapper.java              ← MapStruct Entity ↔ Record
  models/<X>Record.java
  models/<X>Update.java
```

```java
// services/<X>Service.java — interface, no Spring annotations
public interface <X>Service {
    <X>Record findById(Long id);
    <X>Record update(Long id, <X>Update update, AppUser caller);
    Page<<X>Record> search(<X>Query query, Pageable pageable);
}

// services/impl/<X>ServiceImpl.java — ONLY this carries @Service
@Service
@RequiredArgsConstructor
public class <X>ServiceImpl implements <X>Service {

    private final <X>Repository repo;
    private final <X>Mapper mapper;

    @Override
    @LogUsage(action = "<x>.find")
    public <X>Record findById(Long id) {
        return repo.findById(id)
            .map(mapper::toRecord)
            .orElseThrow(() -> new AppException(ErrorReason.C001, id));
    }
    // ...
}
```

### Why interface + impl rather than just the impl class

1. **Testability** — controllers in unit tests mock `<X>Service`, not
   `<X>ServiceImpl`. Mockito handles both, but the typed boundary makes
   intent obvious.
2. **Refactor safety** — adding a second implementation (a `Mock<X>Service`
   for a feature flag, a `Caching<X>ServiceImpl` decorator) doesn't
   require ripping out all the `<X>ServiceImpl` references at call sites.
3. **Self-invocation + AOP** — Spring AOP proxies must be applied at the
   interface seam. If a caller injects the impl class directly, certain
   proxy modes degrade. Interfaces sidestep the issue.

## MapStruct composition via `uses`

When a mapper needs to convert nested types that another mapper already
handles, wire them through `@Mapper(config = ..., uses = ...)` — never
duplicate the conversion code.

```java
// service/.../order/mappers/OrderItemMapper.java
@Mapper(config = ServiceMapperConfig.class)
public interface OrderItemMapper {
    OrderItemRecord toRecord(OrderItemEntity entity);
    OrderItemEntity toEntity(OrderItemRecord record);
}

// service/.../order/mappers/OrderMapper.java
@Mapper(config = ServiceMapperConfig.class, uses = OrderItemMapper.class)
public interface OrderMapper {
    // OrderItemMapper handles List<OrderItem> ↔ List<OrderItemRecord>
    // and Optional<OrderItem> ↔ Optional<OrderItemRecord> automatically.
    // No per-field @Mapping(qualifiedByName=...) needed.
    OrderRecord toRecord(OrderEntity entity);
    OrderEntity toEntity(OrderRecord record);
}
```

Composition through `uses` keeps each mapper small, makes each one
unit-testable in isolation, and lets MapStruct inject the dependency via
Spring (through the shared mapper config) so mappers don't need to manually
instantiate each other.

## Two MapStruct mappers per resource

| Direction | Location | Purpose |
|---|---|---|
| `Entity ↔ ServiceRecord` | `service/` module | Hide JPA, return immutable records to controllers/other services |
| `ServiceRecord ↔ ApiDto` (generated by openapi-generator) | `application/` module | Convert between business records and wire format |

```java
// service/.../<domain>/mappers/<Domain>Mapper.java
@Mapper(config = ServiceMapperConfig.class)
public interface <Domain>Mapper {
    <Domain>Record toRecord(<Domain>Entity entity);
    <Domain>Entity toEntity(<Domain>Record record);
    List<<Domain>Record> toRecords(List<<Domain>Entity> entities);
}

// application/.../<domain>/mappers/<Domain>ApiMapper.java
@Mapper(config = ApplicationMapperConfig.class)
public interface <Domain>ApiMapper {
    <Domain>V1 toDto(<Domain>Record record);                  // V1 DTO from openapi-generator
    <Domain>Record toRecord(Create<Domain>RequestV1 req);     // V1 request from openapi-generator
}
```

Entities never escape the `domain` module — they're not arguments or
return types of any controller, mapper-to-DTO, or external-service call.

## Test method naming + Gherkin body (house style)

Every `@Test` method, JUnit or Vitest, follows this convention:

- **Name:** `shouldDoSomethingTest()` — starts with `should`, describes the
  expected behaviour in camelCase, ends with the literal `Test` suffix.
  Forbidden: `getMeRequiresAuthentication`, `findById_returnsRecord_whenExists`,
  `testFindById`. Required: `shouldRejectUnauthenticatedRequestTest`,
  `shouldThrowAppExceptionWhenEntityMissingTest`.
- **Body:** three Gherkin section markers — `// Given:`, `// When:`,
  `// Then:` — pure separators for arrange / act / assert. **Bare markers
  only — no descriptive text after the colon.** The code below each marker
  IS the description; the comment must not duplicate it. One blank line
  between sections.
- **Vitest:** the `should` opener moves into the `it(...)` string;
  `// Given:` / `// When:` / `// Then:` still structure the body.
- **Empty tests get NO markers.** When the assertion is "the harness loaded
  without throwing" (`@SpringBootTest` smoke, `@DataJpaTest` changelog
  smoke), an empty body is the right form. Don't wrap nothing in
  Given/When/Then.

```java
@Test
void shouldIssueTokenOnMockLoginTest() throws Exception {
    // Given:
    when(mockTokenService.issueToken(eq("alice@example.com")))
        .thenReturn(new MockLoginRecord("mock-jwt", Instant.now().plusSeconds(3600)));

    // When:
    var response = mvc.perform(post("/api/v1/auth/mock/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"alice@example.com\"}"));

    // Then:
    response.andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("mock-jwt"));
}
```

```ts
it("should render the scaffold heading test", () => {
    // Given:
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    // When:
    render(<QueryClientProvider client={qc}><App /></QueryClientProvider>);

    // Then:
    expect(screen.getByRole("heading", { level: 1 })).toBeTruthy();
});
```

**Review check (grep-able):**
```bash
# Find Java test methods NOT matching shouldDoSomethingTest convention.
# Expected: zero output.
grep -rEn 'void [a-z][a-zA-Z0-9_]+\(\)' backend/**/src/test/java/ \
  | grep -vE 'void should[A-Z][a-zA-Z0-9]*Test\('
```
