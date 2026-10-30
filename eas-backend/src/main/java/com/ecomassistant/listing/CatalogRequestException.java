package com.ecomassistant.listing;

/**
 * Thrown for an invalid catalog listing request (unknown category, invalid limit, or a
 * malformed/expired/mismatched cursor). Mapped to HTTP 400 with a single {@code error}
 * field, exposing no internal cursor state.
 */
public class CatalogRequestException extends RuntimeException {

    public CatalogRequestException(String message) {
        super(message);
    }
}
