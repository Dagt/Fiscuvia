package com.fiscuvia.platform;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** JSON integer compatible with Java long and the exact-integer range of JavaScript. */
@NotNull
@Min(0)
@Max(9007199254740991L)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Target({FIELD, METHOD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ExpectedVersion {
    String message() default "La versión es obligatoria y debe ser un entero válido.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
