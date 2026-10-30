package com.ecomassistant.catalog;

import java.util.List;

/**
 * A single node of the browsing category tree, read from the backend configuration
 * resource (mirroring the frontend {@code src/data/categories.json} shape). {@code id}
 * is the stable browsing identity shared with the frontend; {@code name} is a readable
 * reference label used only for embedded audience/ancestry text. {@code imageKey} and any
 * other unknown fields are ignored: they are presentation concerns, not membership
 * semantics.
 */
public record BrowsingCategory(String id, String name, List<BrowsingCategory> children) {

    public BrowsingCategory {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
