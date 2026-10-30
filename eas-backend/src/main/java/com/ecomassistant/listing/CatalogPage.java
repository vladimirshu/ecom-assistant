package com.ecomassistant.listing;

import com.ecomassistant.dto.ProductDto;

import java.util.List;

/**
 * One page of the catalog listing: the products in deterministic traversal order and the
 * opaque cursor for the next page, or {@code null} on the last page.
 */
public record CatalogPage(List<ProductDto> products, String nextCursor) {
}
