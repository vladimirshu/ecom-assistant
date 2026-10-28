package com.ecomassistant.guardrails;

import com.ecomassistant.rag.ProductSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic guardrail around the product-filtering output. It keeps only the
 * retrieved documents whose SKU was selected by the LLM, in retrieval order (so
 * lowest-price ordering survives), drops duplicates and logs (at WARN) selected SKUs that
 * were never retrieved (hallucinations). The result is truncated to {@code maxResults}.
 *
 * <p>The LLM's ordering is ignored by construction: the result is built by iterating the
 * retrieved documents, not the selection.
 */
@Component
public class SkuOutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(SkuOutputGuardrail.class);

    /**
     * Returns the retrieved documents whose SKU is in {@code selection}, in retrieval
     * order, deduplicated and capped at {@code maxResults}. Hallucinated SKUs are logged
     * and dropped.
     *
     * @param documents   the retrieved documents, in retrieval order
     * @param selection   the SKUs selected by the product-filtering call
     * @param maxResults  the maximum number of documents to return
     */
    public List<Document> apply(List<Document> documents, ProductSelection selection, int maxResults) {
        Set<String> selectedSkus = selection == null || selection.skus() == null
                ? Set.of()
                : new HashSet<>(selection.skus());

        warnOnHallucinatedSkus(documents, selectedSkus);

        List<Document> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Document document : documents) {
            String sku = skuOf(document);
            if (selectedSkus.contains(sku) && seen.add(sku)) {
                result.add(document);
            }
        }

        if (result.size() > maxResults) {
            return List.copyOf(result.subList(0, maxResults));
        }
        return List.copyOf(result);
    }

    private static void warnOnHallucinatedSkus(List<Document> documents, Set<String> selectedSkus) {
        Set<String> retrievedSkus = new HashSet<>();
        for (Document document : documents) {
            retrievedSkus.add(skuOf(document));
        }
        for (String sku : selectedSkus) {
            if (!retrievedSkus.contains(sku)) {
                log.warn("Product-filtering output contains a SKU that was not retrieved; dropping it: {}", sku);
            }
        }
    }

    private static String skuOf(Document document) {
        return String.valueOf(document.getMetadata().get("sku"));
    }
}
