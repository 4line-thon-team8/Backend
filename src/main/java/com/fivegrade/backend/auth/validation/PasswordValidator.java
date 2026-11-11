package com.fivegrade.backend.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator
        implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null은 @NotBlank에서 처리한다.
        }
        int length = value.codePointCount(0, value.length());
        return length >= 15 && length <= 64;
    }
}
