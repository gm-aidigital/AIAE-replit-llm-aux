// Marker interface for error-reason enums. Each generated app declares
// its own per-domain enums implementing this (EmployeeErrorReason,
// PaymentErrorReason, …). CommonErrorCodes covers cross-cutting reasons.

package PACKAGE_REPLACE_ME.domain.common.error;

public interface AppErrorReason {
    String getCode();
    String getDescription();
}
