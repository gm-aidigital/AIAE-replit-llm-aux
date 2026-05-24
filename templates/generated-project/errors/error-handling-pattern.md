# Error Handling Pattern (canonical)

Canonical exception/error pattern for generated Java backends. Modelled on
the company's existing `LaboratoryException` / `LaboratoryErrorReason` /
`ValidationMessage` / `ValidationParameter` shape from `cls`. Adopt
verbatim — do **not** invent your own `AppException` / `ErrorResponse`
variants.

## Four parts

```
backend/common/.../error/
  AppException.java          ← unchecked, carries ValidationMessage
  AppErrorReason.java        ← enum: code + parameterised description
  ValidationMessage.java     ← code + formatted message + type + parameters
  ValidationMessageType.java ← enum ERROR | WARN | INFO
  ValidationParameter.java   ← (name, value) pair for param substitution
  CommonErrorCodes.java      ← reusable codes (NOT_FOUND, MALFORMED, …)
  GlobalExceptionHandler.java ← @RestControllerAdvice → translates to ApiError DTO
```

## Shape (copy verbatim into `backend/common/`)

### `AppException.java`

```java
package PACKAGE_REPLACE_ME.common.error;

public class AppException extends RuntimeException {

    private final ValidationMessage validationMessage;

    public AppException(AppErrorReason reason, Object... params) {
        this.validationMessage = ValidationMessage.withParams(reason, params);
    }

    public AppException(AppErrorReason reason, Throwable cause, Object... params) {
        super(cause);
        this.validationMessage = ValidationMessage.withParams(reason, params);
    }

    public AppException(AppErrorReason reason, ValidationParameter... parameters) {
        this.validationMessage = new ValidationMessage(reason, parameters);
    }

    public AppException(AppErrorReason reason, Throwable cause, ValidationParameter... parameters) {
        super(cause);
        this.validationMessage = new ValidationMessage(reason, parameters);
    }

    public ValidationMessage getValidationMessage() { return validationMessage; }
    public String getCode() { return validationMessage.getCode(); }
    @Override public String getMessage() { return String.valueOf(validationMessage); }
    @Override public String getLocalizedMessage() { return getMessage(); }
}
```

### `AppErrorReason.java` (interface — implemented per-domain)

```java
package PACKAGE_REPLACE_ME.common.error;

public interface AppErrorReason {
    String getCode();
    String getDescription();
}
```

Each generated app declares its own enums implementing this — one per
domain area. Naming convention: `<Domain>ErrorReason`. Example shape:

```java
public enum <Domain>ErrorReason implements AppErrorReason {
    E001("E001", "<Domain entity> with id %s not found"),
    E002("E002", "<Other condition>: %s"),
    E003("E003", "<Authorization rule violated>: %s");

    private final String code;
    private final String description;
    <Domain>ErrorReason(String code, String description) { this.code = code; this.description = description; }
    @Override public String getCode() { return code; }
    @Override public String getDescription() { return description; }
}
```

Reusable common codes live in `CommonErrorCodes` (also implements `AppErrorReason`):

```java
public enum CommonErrorCodes implements AppErrorReason {
    UNEXPECTED("C000", "Unexpected error: %s"),
    NOT_FOUND("C001", "Element not found: %s"),
    MALFORMED_REQUEST("C002", "Request is malformed: %s"),
    EXTERNAL_CALL_ERROR("C003", "External call %s failed: %s"),
    FORBIDDEN("C004", "Operation forbidden: %s");
    ...
}
```

### `ValidationMessage.java`

Holds `code`, formatted `message`, `ValidationMessageType` (defaults to
`ERROR`), and a list of `ValidationParameter`. `withParams(reason, vararg Object)`
is the convenience factory used by `AppException`.

(Exact shape mirrors `cls`'s `ValidationMessage` — copy verbatim, replace
`LaboratoryErrorReason` with `AppErrorReason`.)

### `ValidationParameter.java`

Immutable `(code, value)` pair. `toString()` returns `"code:  value"`.

### `ValidationMessageType.java`

```java
public enum ValidationMessageType { ERROR, WARN, INFO }
```

### `GlobalExceptionHandler.java`

`@RestControllerAdvice` that converts `AppException`, Spring's validation
exceptions, and unhandled exceptions into the **canonical `ApiError` DTO**
declared in OpenAPI (see `canonical-openapi-rules.md`). Maps:

| Java exception | HTTP status | ApiError.code source |
|---|---|---|
| `AppException` from auth/security | 401/403 | `validationMessage.getCode()` |
| `AppException` (NOT_FOUND-family) | 404 | `validationMessage.getCode()` |
| `AppException` (other domain errors) | 400 | `validationMessage.getCode()` |
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 | `C002` |
| `AccessDeniedException` | 403 | `C004` |
| `AuthenticationException` | 401 | `C005` |
| `Exception` (last-resort catch-all) | 500 | `C000` (plus log the cause) |

Response body:

```json
{
  "code": "E001",
  "message": "Resource with id 42 not found",
  "timestamp": "2026-05-24T13:00:00Z",
  "correlationId": "<request-id>",
  "parameters": [{"code": "param0", "value": "42"}]
}
```

`code`, `message`, `timestamp`, `correlationId` are the four required fields
in the canonical OpenAPI `ApiError` schema. `parameters` is optional and
exposes the structured params for clients that want to localise.

## Usage in services (canonical)

```java
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository repo;
    private final ResourceMapper mapper;

    public ResourceRecord findById(Long id) {
        return repo.findById(id)
            .map(mapper::toRecord)
            .orElseThrow(() -> new AppException(ResourceErrorReason.E001, id));
    }

    @Transactional
    public ResourceRecord update(Long id, ResourceUpdate update, AppUser caller) {
        ResourceEntity entity = repo.findById(id)
            .orElseThrow(() -> new AppException(ResourceErrorReason.E001, id));
        if (!caller.canEdit(entity)) {
            throw new AppException(ResourceErrorReason.E003, caller.getEmail());
        }
        // ... apply update; service is the only place where business rules live
        return mapper.toRecord(repo.save(entity));
    }
}
```

Controllers do **not** throw `AppException` directly — they catch nothing.
Services throw; the global handler translates to HTTP.

## What NOT to do

- Do not create `RuntimeException` subclasses with hardcoded English strings
  in `super(message)`. All messages flow through `AppErrorReason`.
- Do not use `ResponseStatusException` — bypasses the code/message
  contract.
- Do not catch `AppException` in controllers to remap it. Let the
  `@RestControllerAdvice` own all HTTP translation.
- Do not return raw stack traces or full exception messages to clients.
- Do not use Spring's default `Map<String,Object>` error format — always
  serialise the `ApiError` DTO.
