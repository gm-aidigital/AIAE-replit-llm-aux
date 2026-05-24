// AppException — canonical unchecked exception for ALL business errors.
// Modelled on cls's LaboratoryException. Carries a ValidationMessage
// (code + formatted message + parameters). Caught by GlobalExceptionHandler
// in application/ and converted to the OpenAPI ApiErrorV1 response.

package PACKAGE_REPLACE_ME.domain.common.error;

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

    public ValidationMessage getValidationMessage() {
        return validationMessage;
    }

    public String getCode() {
        return validationMessage.getCode();
    }

    @Override
    public String getMessage() {
        return String.valueOf(validationMessage);
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }
}
