# Error Handling Pattern (canonical)

Canonical pattern. Backend SKILL + safety-review enforce. Never invent
`AppException`/`ErrorResponse` variants; never recreate `AppErrorReason`
interface or per-domain `*ErrorReason` enums.

## Location

```
backend/service/src/main/java/<base>/service/common/error/
  AppException.java          ← unchecked, carries ValidationMessage
  ErrorReason.java           ← THE single enum: code + parameterised description
  ValidationMessage.java     ← code + formatted message + type + parameters
  ValidationMessageType.java ← enum ERROR | WARN | INFO
  ValidationParameter.java   ← (name, value) pair

backend/application/src/main/java/<base>/error/
  GlobalExceptionHandler.java ← @RestControllerAdvice → AppException → ApiErrorV1
```

`AppException`/`ErrorReason`/validation types live in **service** module
(not `domain`) — error semantics are a service-layer concern. Lets
`external-services` drop its `domain` dependency (true leaf); `domain`
stays pure JPA.

## Three rules

1. **ONE enum, `ErrorReason`.** Add codes as the project grows; never sibling enums.
2. **NO `AppErrorReason` interface.** Constructor takes the concrete enum directly.
3. **Only `AppException` flows from service/controller.** Never
   `ResponseStatusException`/`IllegalStateException`/`IllegalArgumentException`/raw
   `RuntimeException` — they bypass `GlobalExceptionHandler` code-prefix mapping
   and surface as opaque 500s.

## `ErrorReason.java`

Enum constant IS the code. No semantic suffix (no `NOT_FOUND`, no
`C001_NOT_FOUND`) — description carries meaning. Value in logs/API responses
matches what you grep for in source.

```java
package PACKAGE_REPLACE_ME.service.common.error;

public enum ErrorReason {

    C000("Unexpected error: %s"),
    C001("Resource not found: %s"),
    C002("Malformed request: %s"),
    C003("External call failed: %s"),
    C004("Access forbidden"),
    C005("Authentication required"),
    C006("Conflict: %s"),
    C007("Rate limit exceeded"),
    // Add domain codes here as the project grows. Format `<L>nnn`:
    //   C — cross-cutting (above)
    //   E — Employee aggregate (E001, E002, …)
    //   O — Order aggregate
    //   <register more letters here as domains land>
    ;

    private final String description;

    ErrorReason(String description) { this.description = description; }

    public String getCode()        { return name(); }
    public String getDescription() { return description; }
}
```

## `AppException.java`

```java
package PACKAGE_REPLACE_ME.service.common.error;

public class AppException extends RuntimeException {

    private final ValidationMessage validationMessage;

    public AppException(ErrorReason reason, Object... params) {
        this.validationMessage = ValidationMessage.withParams(reason, params);
    }

    public AppException(ErrorReason reason, Throwable cause, Object... params) {
        super(cause);
        this.validationMessage = ValidationMessage.withParams(reason, params);
    }

    public AppException(ErrorReason reason, ValidationParameter... parameters) {
        this.validationMessage = new ValidationMessage(reason, parameters);
    }

    public AppException(ErrorReason reason, Throwable cause, ValidationParameter... parameters) {
        super(cause);
        this.validationMessage = new ValidationMessage(reason, parameters);
    }

    public ValidationMessage getValidationMessage() { return validationMessage; }
    public String getCode() { return validationMessage.getCode(); }
    @Override public String getMessage() { return String.valueOf(validationMessage); }
    @Override public String getLocalizedMessage() { return getMessage(); }
}
```

## `ValidationMessage.java`, `ValidationParameter.java`, `ValidationMessageType.java`

Scaffold files at `backend/service/src/main/java/<base>/service/common/error/`
are canonical. Copy verbatim.

`ValidationMessage` constructor: `(ErrorReason reason, ValidationParameter...)`.
`withParams(ErrorReason reason, Object... params)` is `AppException`'s factory
— auto-generates `param0`/`param1` names. `ValidationMessageType` is
`{ ERROR, WARN, INFO }`; AppException always produces `ERROR`.

## `GlobalExceptionHandler.java`

`@RestControllerAdvice` in `application` module — converts `AppException`,
Spring validation, unhandled exceptions to the OpenAPI `ApiErrorV1` DTO.

HTTP status mapping is by `ErrorReason` code prefix:

| Code prefix | HTTP status |
|---|---|
| `C000` | 500 |
| `C001` | 404 |
| `C004` | 403 |
| `C005` | 401 |
| `C006` | 409 |
| `C007` | 429 |
| `C002`, `C003`, other `Cxxx` | 400 |
| Domain codes (`E001`, `O001`, …) | 400 |

When introducing a new prefix that needs a non-400 status, add a branch
to `statusForCode()` — never sprinkle HTTP status logic in services
or controllers.

Spring validation exceptions (`MethodArgumentNotValidException`,
`ConstraintViolationException`) → 400 with `ErrorReason.C002`.
`AccessDeniedException` → 403 with `ErrorReason.C004`.
`AuthenticationException` → 401 with `ErrorReason.C005`.
Last-resort `Exception` catch → 500 with `ErrorReason.C000`.

Response body shape (the OpenAPI `ApiErrorV1` schema):

```json
{
  "code": "C001",
  "message": "Resource not found: 42",
  "timestamp": "2026-05-24T13:00:00",
  "correlationId": "<request-id>",
  "parameters": [{"code": "param0", "value": "42"}]
}
```

`timestamp` is a `LocalDateTime` interpreted as UTC (see backend SKILL
"Time types" — no `OffsetDateTime`).

## Usage in services (canonical)

```java
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository repo;
    private final ResourceMapper mapper;

    @Override
    @LogUsage(action = "resource.find")
    public ResourceRecord findById(Long id) {
        return repo.findById(id)
            .map(mapper::toRecord)
            .orElseThrow(() -> new AppException(ErrorReason.C001, id));
    }

    @Override
    @LogUsage(action = "resource.update")
    public ResourceRecord update(Long id, ResourceUpdate update, AppUser caller) {
        ResourceEntity entity = repo.findById(id)
            .orElseThrow(() -> new AppException(ErrorReason.C001, id));
        if (!caller.canEdit(entity)) {
            throw new AppException(ErrorReason.C004, caller.email());
        }
        applyUpdate(entity, update);
        return mapper.toRecord(repo.save(entity));
    }

    /**
     * Copies the writable fields from the update record onto the entity.
     * Kept private so the public method body reads top-to-bottom.
     */
    private void applyUpdate(ResourceEntity entity, ResourceUpdate update) {
        entity.apply(update);
    }
}
```

Controllers do **not** throw `AppException` directly — they catch nothing.
Services throw; the global handler translates to HTTP.

## What NOT to do (Agent has done all of these)

- No `RuntimeException` subclasses with hard-coded English in `super(message)` — flow through `ErrorReason`.
- No `AppErrorReason` interface; no per-domain `<Domain>ErrorReason`/`<Domain>ErrorCodes`. One concrete enum.
- No `ResponseStatusException` — bypasses the code/message contract.
- No `catch (AppException)` in controllers — `@RestControllerAdvice` owns HTTP translation.
- No raw stack traces or full exception messages to clients.
- No `Map<String,Object>` error format — always `ApiErrorV1`.
- Controllers never throw. Validation annotations on request DTOs trigger
  `MethodArgumentNotValidException` → handler → 400.
