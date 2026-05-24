// GlobalExceptionHandler — single source of HTTP error translation.
// Catches AppException + Spring validation/security exceptions and converts
// them to the OpenAPI-generated ApiErrorV1 DTO. Controllers throw nothing
// of their own; services throw AppException.

package PACKAGE_REPLACE_ME.error;

import PACKAGE_REPLACE_ME.api.v1.model.ApiErrorV1;
import PACKAGE_REPLACE_ME.api.v1.model.ValidationParameterV1;
import PACKAGE_REPLACE_ME.domain.common.error.AppException;
import PACKAGE_REPLACE_ME.domain.common.error.CommonErrorCodes;
import PACKAGE_REPLACE_ME.domain.common.error.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** MDC key for the per-request correlation id (set by a request filter). */
    private static final String MDC_CORRELATION_ID = "correlationId";

    /** Error-code prefixes that map to specific HTTP statuses. */
    private static final String NOT_FOUND_PREFIX  = "C001";
    private static final String FORBIDDEN_PREFIX  = "C004";
    private static final String UNAUTH_PREFIX     = "C005";
    private static final String CONFLICT_PREFIX   = "C006";
    private static final String RATE_LIMIT_PREFIX = "C007";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorV1> handleAppException(AppException ex) {
        HttpStatus status = statusForCode(ex.getCode());
        if (status.is5xxServerError()) {
            LOG.error("AppException {}: {}", ex.getCode(), ex.getMessage(), ex);
        } else {
            LOG.warn("AppException {}: {}", ex.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(status).body(toDto(ex.getValidationMessage()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class
    })
    public ResponseEntity<ApiErrorV1> handleValidation(Exception ex) {
        LOG.warn("Validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            toDto(new ValidationMessage(CommonErrorCodes.MALFORMED_REQUEST,
                new PACKAGE_REPLACE_ME.domain.common.error.ValidationParameter("detail", ex.getMessage()))));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorV1> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            toDto(new ValidationMessage(CommonErrorCodes.UNAUTHENTICATED)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorV1> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            toDto(new ValidationMessage(CommonErrorCodes.FORBIDDEN,
                new PACKAGE_REPLACE_ME.domain.common.error.ValidationParameter("detail", ex.getMessage()))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorV1> handleUnknown(Exception ex) {
        LOG.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            toDto(new ValidationMessage(CommonErrorCodes.UNEXPECTED,
                new PACKAGE_REPLACE_ME.domain.common.error.ValidationParameter("class", ex.getClass().getSimpleName()))));
    }

    // ----- helpers -----

    private static HttpStatus statusForCode(String code) {
        if (code == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        if (code.startsWith(NOT_FOUND_PREFIX))  return HttpStatus.NOT_FOUND;
        if (code.startsWith(FORBIDDEN_PREFIX))  return HttpStatus.FORBIDDEN;
        if (code.startsWith(UNAUTH_PREFIX))     return HttpStatus.UNAUTHORIZED;
        if (code.startsWith(CONFLICT_PREFIX))   return HttpStatus.CONFLICT;
        if (code.startsWith(RATE_LIMIT_PREFIX)) return HttpStatus.TOO_MANY_REQUESTS;
        if (code.startsWith("C000"))            return HttpStatus.INTERNAL_SERVER_ERROR;
        // All other "Cxxx" codes and any domain-specific code → 400.
        return HttpStatus.BAD_REQUEST;
    }

    private ApiErrorV1 toDto(ValidationMessage msg) {
        ApiErrorV1 dto = new ApiErrorV1();
        dto.setCode(msg.getCode());
        dto.setMessage(msg.getMessage());
        dto.setTimestamp(OffsetDateTime.now());
        dto.setCorrelationId(MDC.get(MDC_CORRELATION_ID));
        List<ValidationParameterV1> params = msg.getParameters().stream()
            .map(p -> {
                ValidationParameterV1 v = new ValidationParameterV1();
                v.setCode(p.getCode());
                v.setValue(p.getValue());
                return v;
            })
            .collect(Collectors.toList());
        dto.setParameters(params);
        return dto;
    }
}
