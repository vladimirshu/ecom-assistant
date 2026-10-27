package com.ecomassistant.validation;

import java.text.Normalizer;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
        String trimmed = nfkc.trim();
        return trimmed.replaceAll("\\s+", " ");
    }
}
