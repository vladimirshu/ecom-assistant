package com.ecomassistant.listing;

/**
 * The opaque cursor envelope bound to a catalog revision, category scope, page size and
 * traversal position. {@code category} is {@code null} for the unfiltered "all clothing"
 * scope. The envelope is serialized, signed and base64url-encoded by
 * {@link CatalogCursorCodec}; no field is ever exposed to the client.
 */
public record CatalogCursor(String revision, String category, int limit, int offset, long issuedAt) {
}
