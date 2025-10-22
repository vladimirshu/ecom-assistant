package com.ecomassistant.utils;

import com.ecomassistant.exception.IllegalAssistantResponseException;
import io.micrometer.common.util.StringUtils;
import org.apache.commons.validator.routines.RegexValidator;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.regex.Pattern;

public final class ValidationUtils {
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)<script[^>]*>.*?</script>|javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|sp_|xp_)|(--)|(;)|('|(\\|\\|))",
            Pattern.CASE_INSENSITIVE
    );
    private static final RegexValidator ALPHANUMERIC_VALIDATOR = new RegexValidator("^[a-zA-Z0-9\\-_]+$");

    private ValidationUtils() {
    }

    public static String validateAndSanitize(String input) {
        validate(input);
        return sanitize(input);
    }

    /**
     * Sanitizes user message input by removing HTML tags and potential XSS vectors
     */
    public static String sanitize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }
        // Remove HTML tags
        String sanitized = POLICY.sanitize(input);

        return sanitized.trim();
    }

    public static void validateAssistantMessage(AssistantMessage assistantMessage) {
        try {
            validate(assistantMessage.getText());
        } catch (IllegalArgumentException e) {
            throw new IllegalAssistantResponseException(e.getMessage(), sanitize(assistantMessage.getText()));
        }
    }

    private static void validate(String content) {
        // Additional XSS protection
        if (XSS_PATTERN.matcher(content).find()) {
            throw new SecurityException("Potentially malicious content detected in message");
        }

        // Basic SQL injection pattern detection
        if (SQL_INJECTION_PATTERN.matcher(content).find()) {
            throw new SecurityException("Potentially malicious content detected in message");
        }

        // Validate input length
        if (StringUtils.isEmpty(content) || content.length() > 1000) {
            throw new IllegalArgumentException("Input must be between 1 and 1000 characters");
        }

        // Check for control characters
        if (content.chars().anyMatch(ch -> Character.isISOControl(ch) && !Character.isWhitespace(ch))) {
            throw new IllegalArgumentException("Input contains invalid control characters");
        }

        // Check for URLs
        if (content.matches(".*((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]).*")) {
            throw new IllegalArgumentException("URLs are not allowed in the input");
        }

        // Check for HTML tags
        if (content.matches(".*<[^>]+>.*")) {
            throw new IllegalArgumentException("HTML tags are not allowed in the input");
        }

        // Check for special characters sequences that might indicate code or scripts
        if (content.matches(".*[{\\[<>\\]}].*") || content.matches(".*\\$\\{.*\\}.*") || content.contains(
                "javascript:") || content.contains("data:")) {
            throw new IllegalArgumentException(
                    "Input contains invalid special characters or potentially harmful content");
        }
    }
}
