package com.ecomassistant.catalog.facts;

/**
 * A single material fiber and its share of the enclosing component. {@code percent} is the
 * integer percentage of that component made of this fiber; within a {@code known} component
 * the percentages must sum to 100. The fiber name uses the controlled vocabulary documented
 * in the authoring-facts schema so that exclusion judgments (no wool/animal fiber, no down,
 * no leather) are deterministic rather than inferred from vague marketing words.
 */
public record Fiber(String fiber, int percent) {

    public Fiber {
        fiber = fiber == null ? "" : fiber.trim();
    }
}
