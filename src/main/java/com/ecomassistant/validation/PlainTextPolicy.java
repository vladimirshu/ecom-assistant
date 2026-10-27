package com.ecomassistant.validation;

import com.ecomassistant.exception.IllegalAssistantResponseException;

public final class PlainTextPolicy {

    private PlainTextPolicy() {
    }

    public static String requirePlainText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            throw new IllegalAssistantResponseException("Assistant response must not be blank", text);
        }
        if (text.length() > maxLength) {
            throw new IllegalAssistantResponseException(
                    "Assistant response exceeds the maximum length of " + maxLength, text);
        }
        if (text.chars().anyMatch(ch -> Character.isISOControl(ch) && !Character.isWhitespace(ch))) {
            throw new IllegalAssistantResponseException("Assistant response contains invalid control characters", text);
        }
        return text;
    }
}
