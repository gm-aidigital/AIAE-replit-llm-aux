// Cross-cutting error reasons. Domain-specific reasons live in their own
// enums (e.g. EmployeeErrorReason) that also implement AppErrorReason.

package PACKAGE_REPLACE_ME.domain.common.error;

public enum CommonErrorCodes implements AppErrorReason {

    UNEXPECTED         ("C000", "Unexpected error: %s"),
    NOT_FOUND          ("C001", "Element of type %s with id %s not found"),
    MALFORMED_REQUEST  ("C002", "Request is malformed: %s"),
    EXTERNAL_CALL      ("C003", "External call %s failed: %s"),
    FORBIDDEN          ("C004", "Operation forbidden: %s"),
    UNAUTHENTICATED    ("C005", "Authentication required"),
    CONFLICT           ("C006", "Conflict: %s"),
    RATE_LIMITED       ("C007", "Rate limit exceeded for %s");

    private final String code;
    private final String description;

    CommonErrorCodes(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override public String getCode()        { return code; }
    @Override public String getDescription() { return description; }
}
