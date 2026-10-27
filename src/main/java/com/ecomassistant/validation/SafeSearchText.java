package com.ecomassistant.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SafeSearchTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeSearchText {

    String message() default "Message must be 3 to 500 characters and contain only letters, digits, spaces and basic punctuation";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
