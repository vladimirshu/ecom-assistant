package com.ecomassistant.catalog.facts;

import java.util.List;

/**
 * A single garment material component (shell, lining or fill). {@code state} is one of
 * {@code known}, {@code unknown} or {@code not-applicable}; only a {@code known} component
 * carries fibers. {@code unknown} means the author has not established the composition, so
 * exclusion judgments must not treat the component as confirmed free of an excluded
 * material. {@code not-applicable} means the garment has no such component at all.
 */
public record MaterialComponent(String state, List<Fiber> fibers) {

    public static final String KNOWN = "known";
    public static final String UNKNOWN = "unknown";
    public static final String NOT_APPLICABLE = "not-applicable";

    public MaterialComponent {
        state = state == null || state.isBlank() ? NOT_APPLICABLE : state;
        fibers = fibers == null ? List.of() : List.copyOf(fibers);
    }

    public static MaterialComponent notApplicable() {
        return new MaterialComponent(NOT_APPLICABLE, List.of());
    }

    public static MaterialComponent known(List<Fiber> fibers) {
        return new MaterialComponent(KNOWN, fibers);
    }

    public static MaterialComponent unknown() {
        return new MaterialComponent(UNKNOWN, List.of());
    }

    public boolean isKnown() {
        return KNOWN.equals(state);
    }

    public boolean isUnknown() {
        return UNKNOWN.equals(state);
    }
}
