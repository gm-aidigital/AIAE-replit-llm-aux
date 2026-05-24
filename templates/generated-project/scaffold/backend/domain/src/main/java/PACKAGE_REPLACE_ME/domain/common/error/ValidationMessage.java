// ValidationMessage — code + formatted message + type + parameters.
// Mirrors cls's ValidationMessage. Created by AppException; serialised
// into the ApiErrorV1 response body by GlobalExceptionHandler.

package PACKAGE_REPLACE_ME.domain.common.error;

import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ValidationMessage {

    private final String code;
    private final String message;
    private final ValidationMessageType type;
    private final List<ValidationParameter> parameters;

    public ValidationMessage(@NotNull AppErrorReason reason, ValidationParameter... params) {
        this.code = reason.getCode();
        this.type = ValidationMessageType.ERROR;
        this.parameters = params != null ? Arrays.asList(params) : Collections.emptyList();

        if (!this.parameters.isEmpty()) {
            Object[] paramValues = this.parameters.stream()
                .map(ValidationParameter::getValue)
                .toArray();
            this.message = String.format(reason.getDescription(), paramValues);
        } else {
            this.message = reason.getDescription();
        }
    }

    public String getCode()                          { return code; }
    public String getMessage()                       { return message; }
    public ValidationMessageType getType()           { return type; }
    public List<ValidationParameter> getParameters() { return parameters; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidationMessage that)) return false;
        return Objects.equals(code, that.code)
            && Objects.equals(message, that.message)
            && type == that.type
            && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, type, parameters);
    }

    @Override
    public String toString() {
        return String.format("%s: %s", code, message);
    }

    /** Convenience factory: positional params become ValidationParameter("param0", str), … */
    public static ValidationMessage withParams(AppErrorReason reason, Object... params) {
        ValidationParameter[] validationParameters = null;
        if (params != null) {
            validationParameters = new ValidationParameter[params.length];
            for (int i = 0; i < params.length; i++) {
                validationParameters[i] = new ValidationParameter("param" + i, String.valueOf(params[i]));
            }
        }
        return new ValidationMessage(reason, validationParameters);
    }
}
