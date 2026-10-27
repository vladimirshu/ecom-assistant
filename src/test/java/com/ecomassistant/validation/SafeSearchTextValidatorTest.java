package com.ecomassistant.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeSearchTextValidatorTest {

    private final SafeSearchTextValidator validator = new SafeSearchTextValidator();

    @Test
    void acceptsApostrophe() {
        assertTrue(validator.isValid("I'd like a waterproof jacket", null));
    }

    @Test
    void acceptsFormerSqlKeywords() {
        assertTrue(validator.isValid("alternative to an executive coat with dropped shoulders", null));
    }

    @Test
    void acceptsNonLatinScript() {
        assertTrue(validator.isValid("зимняя куртка до 200 евро", null));
    }

    @Test
    void acceptsAmpersand() {
        assertTrue(validator.isValid("M&S style rain jacket", null));
    }

    @Test
    void rejectsAngleBrackets() {
        assertFalse(validator.isValid("jacket <script>", null));
    }

    @Test
    void rejectsTooShortMessage() {
        assertFalse(validator.isValid("ab", null));
    }

    @Test
    void acceptsMinimumLength() {
        assertTrue(validator.isValid("abc", null));
    }

    @Test
    void rejectsTooLongMessage() {
        assertFalse(validator.isValid("a".repeat(501), null));
    }

    @Test
    void acceptsMaximumLength() {
        assertTrue(validator.isValid("a".repeat(500), null));
    }

    @Test
    void rejectsNull() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void rejectsEmpty() {
        assertFalse(validator.isValid("", null));
    }
}
