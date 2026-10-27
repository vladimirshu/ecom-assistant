package com.ecomassistant.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextNormalizerTest {

    @Test
    void normalizesFullWidthCharacters() {
        assertEquals("jacket under 200", TextNormalizer.normalize("ｊａｃｋｅｔ  under ２００"));
    }

    @Test
    void collapsesRepeatedAndSurroundingWhitespace() {
        assertEquals("blue jacket", TextNormalizer.normalize("  blue    jacket "));
    }

    @Test
    void returnsNullForNullInput() {
        assertNull(TextNormalizer.normalize(null));
    }

    @Test
    void returnsEmptyForBlankInput() {
        assertEquals("", TextNormalizer.normalize("   "));
    }
}
