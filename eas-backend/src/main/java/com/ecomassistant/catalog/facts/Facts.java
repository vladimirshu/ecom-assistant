package com.ecomassistant.catalog.facts;

import java.util.List;

/**
 * Authoring-only functional facts for a product. These are the source of truth for the
 * offline quality gate and the relevance benchmark's exclusion judgments. They are never
 * exposed on the public {@link com.ecomassistant.dto.ProductDto} and never injected into
 * runtime search; every fact a benchmark query depends on must also appear naturally in
 * the product's title/description/colors/sizes/category data.
 *
 * <p>String-valued dimensions use the controlled vocabulary validated by the quality gate;
 * {@code unknown} is distinct from {@code not-applicable} and from an absent/false value.
 */
public record Facts(
        Materials materials,
        String warmth,
        List<String> seasons,
        String weatherProtection,
        String windProtection,
        String hood,
        String fit,
        String stretch,
        List<String> pockets,
        Boolean packable,
        List<String> occasions,
        List<String> limitations) {

    public Facts {
        seasons = seasons == null ? List.of() : List.copyOf(seasons);
        pockets = pockets == null ? List.of() : List.copyOf(pockets);
        occasions = occasions == null ? List.of() : List.copyOf(occasions);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
