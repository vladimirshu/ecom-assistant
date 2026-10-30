package com.ecomassistant.relevance;

import com.ecomassistant.catalog.facts.FactProduct;
import com.ecomassistant.catalog.facts.CatalogFactsLoader;
import com.ecomassistant.dto.ProductDto;
import com.ecomassistant.service.ProductSearchService;
import com.ecomassistant.service.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in, isolated-catalog search evaluation. This is wired only when
 * {@code ecom.relevance.evaluation.enabled=true}; ordinary builds and unit tests never
 * instantiate it, so no external model or vector-store call happens by default.
 *
 * <p>It drives the real {@link ProductSearchService} end-to-end pipeline once per
 * conversation turn, records bounded per-case diagnostics, and computes the acceptance
 * metrics defined in {@code relevance-plan.md}. Candidate-stage (Hit@30) measurement is a
 * separate concern of the retrieval layer and is recorded as a named metric only when the
 * actual configured topK is available.
 */
@Component
@ConditionalOnProperty(name = "ecom.relevance.evaluation.enabled", havingValue = "true")
public class SearchEvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchEvaluationRunner.class);

    private final ProductSearchService searchService;
    private final Map<String, FactProduct> catalogBySku;

    public SearchEvaluationRunner(ProductSearchService searchService) {
        this.searchService = searchService;
        Map<String, FactProduct> index = new java.util.HashMap<>();
        for (FactProduct product : CatalogFactsLoader
                .loadAllClasspath(CatalogFactsLoader.AUTHORING_PARTS).products()) {
            index.put(product.sku(), product);
        }
        this.catalogBySku = Map.copyOf(index);
    }

    /** One conversation turn's measured output. */
    public record TurnResult(int turnIndex, String query, List<String> returnedSkus) {
    }

    /** One judged case's measured output plus its fixed judgments. */
    public record CaseResult(
            String caseId,
            List<TurnResult> turns,
            List<String> hardViolationSkus,
            boolean emptyAndExpected,
            boolean falseEmpty) {
    }

    /** Recorded configuration that affects results. */
    public record ConfigSnapshot(
            String providerModel,
            int topK,
            double similarityThreshold,
            int maxResults,
            String sourceCatalogRevision) {
    }

    /** A bounded evaluation report; per-case detail is retained, not just aggregates. */
    public record RunReport(
            Instant startedAt,
            ConfigSnapshot config,
            List<CaseResult> cases,
            double meanPrecisionAt5,
            double hitAt3,
            int hardViolationCount,
            int falseEmptyCount) {
    }

    /** Runs the whole suite end-to-end and returns a bounded report. */
    public RunReport run(RelevanceSuite suite, ConfigSnapshot config) {
        List<CaseResult> results = new ArrayList<>();
        double precisionSum = 0.0;
        int precisionCases = 0;
        int hitCount = 0;
        int hitCases = 0;
        int hardViolations = 0;
        int falseEmpties = 0;

        for (RelevanceSuite.RelevanceCase c : suite.cases()) {
            String conversationId = "eval-" + c.id() + "-" + Instant.now().toEpochMilli();
            List<TurnResult> turns = new ArrayList<>();
            List<String> lastReturned = List.of();
            for (int i = 0; i < c.turns().size(); i++) {
                SearchResult result = searchService.searchProducts(c.turns().get(i), conversationId);
                List<String> skus = result.products().stream().map(ProductDto::sku).toList();
                turns.add(new TurnResult(i, c.turns().get(i), skus));
                lastReturned = skus;
            }

            List<String> violations = hardConstraintViolations(lastReturned, c, catalogBySku);
            boolean emptyAndExpected = c.expectedEmpty() && lastReturned.isEmpty();
            boolean falseEmpty = !c.expectedEmpty() && lastReturned.isEmpty();

            if (!c.expectedEmpty()) {
                precisionCases++;
                precisionSum += precisionAtK(lastReturned, c.eligibleSkus(), 5);
                hitCases++;
                if (hitAtK(lastReturned, c.eligibleSkus(), 3)) {
                    hitCount++;
                }
            }
            hardViolations += violations.size();
            if (falseEmpty) {
                falseEmpties++;
            }

            results.add(new CaseResult(c.id(), turns, violations, emptyAndExpected, falseEmpty));
        }

        double meanPrecisionAt5 = precisionCases == 0 ? 1.0 : precisionSum / precisionCases;
        double hitAt3 = hitCases == 0 ? 1.0 : (double) hitCount / hitCases;
        return new RunReport(Instant.now(), config, results, meanPrecisionAt5, hitAt3,
                hardViolations, falseEmpties);
    }

    // --- Metric helpers (pure, unit-testable) -----------------------------------------

    /** Proportion of the first {@code min(k, returned)} results that are judged eligible. */
    public static double precisionAtK(List<String> returned, List<String> eligible, int k) {
        Set<String> eligibleSet = new HashSet<>(eligible);
        int limit = Math.min(k, returned.size());
        if (limit == 0) {
            return 0.0;
        }
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (eligibleSet.contains(returned.get(i))) {
                hits++;
            }
        }
        return (double) hits / limit;
    }

    /** Whether any judged-eligible SKU appears within the first {@code k} results. */
    public static boolean hitAtK(List<String> returned, List<String> eligible, int k) {
        Set<String> eligibleSet = new HashSet<>(eligible);
        int limit = Math.min(k, returned.size());
        for (int i = 0; i < limit; i++) {
            if (eligibleSet.contains(returned.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Returned SKUs that violate the case's fact-derivable hard constraints. */
    public static List<String> hardConstraintViolations(List<String> returned,
            RelevanceSuite.RelevanceCase c, Map<String, FactProduct> catalogBySku) {
        List<String> violations = new ArrayList<>();
        for (String sku : returned) {
            FactProduct product = catalogBySku.get(sku);
            if (product != null && !RelevanceSuite.satisfiesHardConstraints(product, c.hardConstraints())) {
                violations.add(sku);
            }
        }
        return violations;
    }
}
