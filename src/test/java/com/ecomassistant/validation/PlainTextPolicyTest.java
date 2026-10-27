package com.ecomassistant.validation;

import com.ecomassistant.exception.IllegalAssistantResponseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlainTextPolicyTest {

    @Test
    void returnsPlainTextUnchanged() {
        assertEquals("M&S jacket", PlainTextPolicy.requirePlainText("M&S jacket", 1000));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalAssistantResponseException.class, () -> PlainTextPolicy.requirePlainText(null, 1000));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalAssistantResponseException.class, () -> PlainTextPolicy.requirePlainText("   ", 1000));
    }

    @Test
    void rejectsTooLong() {
        assertThrows(IllegalAssistantResponseException.class, () -> PlainTextPolicy.requirePlainText("a".repeat(11), 10));
    }

    @Test
    void rejectsControlCharacters() {
        assertThrows(IllegalAssistantResponseException.class, () -> PlainTextPolicy.requirePlainText("bad\u0007text", 1000));
    }

    @Test
    void allowsWhitespace() {
        assertEquals("a b\tc", PlainTextPolicy.requirePlainText("a b\tc", 1000));
    }
}
