package com.ecomassistant.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SafeSearchTextValidator implements ConstraintValidator<SafeSearchText, String> {

    private static final Pattern WHITELIST = Pattern.compile("^[\\p{L}\\p{M}\\p{N}\\s'’\"\\-_.,!?()€$£¥%/&+]+$");
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 500;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            return false;
        }
        return WHITELIST.matcher(value).matches();
    }
}
