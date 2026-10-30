package com.ecomassistant.relevance;

import com.ecomassistant.catalog.facts.CatalogFacts;
import com.ecomassistant.catalog.facts.CatalogFactsLoader;
import com.ecomassistant.catalog.facts.FactProduct;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural and fact-based validation of the judged search-scenario suite. Verifies the
 * 44-case allocation, held-out split, SKU cross-references against the authored catalog,
 * and that fact-derivable hard constraints (price, material exclusions, protection, hood,
 * packable, stretch, root) agree with the authored profiles. No model or vector store is
 * involved.
 */
class RelevanceCasesTest {

    private static final List<String> PARTS = List.of(
            "sampledata/facts/men-jackets-coats.json",
            "sampledata/facts/men-knitwear.json",
            "sampledata/facts/men-bottoms.json",
            "sampledata/facts/women-jackets-coats.json",
            "sampledata/facts/women-knitwear.json",
            "sampledata/facts/women-bottoms.json");

    private final CatalogFacts catalog = CatalogFactsLoader.loadAllClasspath(PARTS);
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
    void suiteHasTheRequiredAllocation() {
        Map<String, Integer> byFamily = new HashMap<>();
        for (RelevanceSuite.RelevanceCase c : suite.cases()) {
            byFamily.merge(c.family(), 1, Integer::sum);
        }
        assertThat(suite.cases()).hasSize(44);
        assertThat(byFamily).containsAllEntriesOf(Map.of(
                "root-group", 8,
                "positive", 12,
                "exclusion", 8,
                "paraphrase", 4,
                "non-english", 4,
                "conversation", 4,
                "empty", 4));
    }

    @Test
    void atLeastOneQuarterIsHeldOut() {
        long heldOut = suite.cases().stream().filter(RelevanceSuite.RelevanceCase::isHeldOut).count();
        assertThat(heldOut).isGreaterThanOrEqualTo((long) Math.ceil(suite.cases().size() / 4.0));
    }

    @Test
    void everyReferencedSkuExistsAndJudgmentsAreDisjoint() {
        for (RelevanceSuite.RelevanceCase c : suite.cases()) {
            Set<String> eligible = new HashSet<>(c.eligibleSkus());
            Set<String> acceptable = new HashSet<>(c.acceptableSkus());
            Set<String> nearMiss = new HashSet<>();
            for (RelevanceSuite.NearMiss nm : c.nearMissSkus()) {
                nearMiss.add(nm.sku());
            }

            for (String sku : c.eligibleSkus()) {
                assertThat(bySku).as("eligible SKU " + sku + " in case " + c.id()).containsKey(sku);
            }
            for (String sku : c.acceptableSkus()) {
                assertThat(bySku).as("acceptable SKU " + sku + " in case " + c.id()).containsKey(sku);
            }
            for (String sku : nearMiss) {
                assertThat(bySku).as("near-miss SKU " + sku + " in case " + c.id()).containsKey(sku);
            }

            if (!nearMiss.isEmpty()) {
                assertThat(eligible).doesNotContainAnyElementsOf(nearMiss);
                assertThat(acceptable).doesNotContainAnyElementsOf(nearMiss);
            }
        }
    }

    @Test
    void positiveAndEmptyInvariantsHold() {
        for (RelevanceSuite.RelevanceCase c : suite.cases()) {
            if (c.expectedEmpty()) {
                assertThat(c.eligibleSkus()).isEmpty();
                assertThat(c.acceptableSkus()).isEmpty();
            }
            else if (c.broad()) {
                assertThat(c.eligibleSkus()).as("broad case " + c.id()).hasSizeGreaterThanOrEqualTo(3);
            }
            else {
                assertThat(c.eligibleSkus()).as("positive case " + c.id()).isNotEmpty();
            }
        }
    }

    @Test
    void eligibleSkusSatisfyFactDerivableHardConstraints() {
        for (RelevanceSuite.RelevanceCase c : suite.cases()) {
            if (c.expectedEmpty()) {
                continue;
            }
            for (String sku : c.eligibleSkus()) {
                FactProduct product = bySku.get(sku);
                boolean ok = RelevanceSuite.satisfiesHardConstraints(product, c.hardConstraints());
                assertThat(ok).as("eligible SKU " + sku + " violates hard constraints of case " + c.id()
                        + ": " + c.hardConstraints()).isTrue();
            }
        }
    }

    @Test
    void nearMissSkusAreNotListedAsEligibleOrAcceptable() {
        for (RelevanceSuite.RelevanceCase c : suite.cases()) {
            Set<String> eligible = new HashSet<>(c.eligibleSkus());
            Set<String> acceptable = new HashSet<>(c.acceptableSkus());
            for (RelevanceSuite.NearMiss nm : c.nearMissSkus()) {
                assertThat(eligible).as("near-miss " + nm.sku() + " also eligible in " + c.id())
                        .doesNotContain(nm.sku());
                assertThat(acceptable).as("near-miss " + nm.sku() + " also acceptable in " + c.id())
                        .doesNotContain(nm.sku());
            }
        }
    }
}
