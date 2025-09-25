package com.ecomassistant.service;

import org.apache.commons.validator.routines.RegexValidator;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class InputSanitizationService {

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

    /**
     * Sanitizes user message input by removing HTML tags and potential XSS vectors
     */
    public String sanitizeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        // Remove HTML tags
        String sanitized = POLICY.sanitize(input);

        // Additional XSS protection
        if (XSS_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Potentially malicious content detected in message");
        }

        // Basic SQL injection pattern detection
        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Potentially malicious content detected in message");
        }

        return sanitized.trim();
    }

}
