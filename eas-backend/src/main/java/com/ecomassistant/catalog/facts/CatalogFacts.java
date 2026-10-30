package com.ecomassistant.catalog.facts;

import java.util.List;
import java.util.Map;

/**
 * The versioned authoring-facts document: schema version, catalog id, provenance notes and
 * the ordered list of authored products. The facts document is the source of truth; the
 * import CSV is an export of it, verified for parity so the two cannot drift independently.
 */
public record CatalogFacts(
        int schemaVersion,
        String catalogId,
        Map<String, Object> provenance,
        List<FactProduct> products) {

    public CatalogFacts {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
