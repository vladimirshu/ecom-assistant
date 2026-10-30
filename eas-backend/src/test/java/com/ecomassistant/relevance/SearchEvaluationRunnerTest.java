package com.ecomassistant.relevance;

import com.ecomassistant.catalog.facts.CatalogFacts;
import com.ecomassistant.catalog.facts.CatalogFactsLoader;
import com.ecomassistant.catalog.facts.FactProduct;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure metric helpers of the opt-in evaluation runner. No Spring context, model or vector
 * store is involved.
 */
class SearchEvaluationRunnerTest {

    private final CatalogFacts catalog = CatalogFactsLoader.loadAllClasspath(CatalogFactsLoader.AUTHORING_PARTS);
    private final Map<String, FactProduct> bySku = index(catalog);
    private final RelevanceSuite suite = RelevanceSuite.loadClasspath("relevance/seed-search-cases.json");

    private static Map<String, FactProduct> index(CatalogFacts facts) {
        Map<String, FactProduct> map = new HashMap<>();
        for (FactProduct product : facts.products()) {
            map.put(product.sku(), product);
        }
        return map;
    }

    @Test
    void precisionAndHitMetrics() {
        List<String> returned = List.of("M-RAN-01", "M-RAN-02", "M-SFT-02", "X", "Y");
        List<String> eligible = List.of("M-RAN-01", "M-RAN-02");

        assertThat(SearchEvaluationRunner.precisionAtK(returned, eligible, 5)).isEqualTo(2.0 / 5.0);
        assertThat(SearchEvaluationRunner.hitAtK(returned, eligible, 3)).isTrue();
        assertThat(SearchEvaluationRunner.hitAtK(List.of("X", "Y"), eligible, 3)).isFalse();
        assertThat(SearchEvaluationRunner.precisionAtK(List.of(), eligible, 5)).isZero();
    }

    @Test
    void hardConstraintViolationsFlagWrongMaterial() {
        RelevanceSuite.RelevanceCase noDown = suite.cases().stream()
                .filter(c -> c.id().equals("pos-no-down-puffer"))
                .findFirst().orElseThrow();

        // M-PUF-01 is animal down, so it must be flagged as a violation for a no-down case.
        List<String> violations = SearchEvaluationRunner.hardConstraintViolations(
                List.of("M-PUF-01", "M-PUF-02"), noDown, bySku);

        assertThat(violations).containsExactly("M-PUF-01");
    }
}
