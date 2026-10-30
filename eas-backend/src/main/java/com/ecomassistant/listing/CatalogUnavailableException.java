package com.ecomassistant.listing;

/**
 * Thrown when catalog listing cannot serve a verified complete catalog (import not yet
 * verified, or the vector store is unavailable). Mapped to HTTP 503, never a successful
 * partial page.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message) {
        super(message);
    }
}
