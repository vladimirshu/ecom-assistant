package com.ecomassistant.guardrails;

import com.ecomassistant.rag.ProductSelection;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkuOutputGuardrailTest {

    private final SkuOutputGuardrail guardrail = new SkuOutputGuardrail();

    @Test
    void keepsOnlyRetrievedAndSelectedSkusInRetrievalOrder() {
        List<Document> documents = List.of(doc("A"), doc("B"), doc("C"));

        List<Document> result = guardrail.apply(documents, new ProductSelection(List.of("C", "A")), 10);

        assertThat(skus(result)).containsExactly("A", "C");
    }

    @Test
    void dropsHallucinatedSkus() {
        List<Document> documents = List.of(doc("A"), doc("B"));

        List<Document> result = guardrail.apply(documents, new ProductSelection(List.of("B", "HALLUCINATED")), 10);

        assertThat(skus(result)).containsExactly("B");
    }

    @Test
    void duplicateSelectionsAreReturnedOnce() {
        List<Document> documents = List.of(doc("A"), doc("B"));

        List<Document> result = guardrail.apply(documents, new ProductSelection(List.of("A", "A", "B")), 10);

        assertThat(skus(result)).containsExactly("A", "B");
    }

    @Test
    void orderFollowsRetrievalOrderRegardlessOfSelectionOrder() {
        List<Document> documents = List.of(doc("A"), doc("B"), doc("C"));

        List<Document> result = guardrail.apply(documents, new ProductSelection(List.of("B", "A")), 10);

        assertThat(skus(result)).containsExactly("A", "B");
    }

    @Test
    void capsAtMaxResults() {
        List<Document> documents = List.of(doc("A"), doc("B"), doc("C"), doc("D"));

        List<Document> result = guardrail.apply(documents,
                new ProductSelection(List.of("A", "B", "C", "D")), 3);

        assertThat(skus(result)).containsExactly("A", "B", "C");
    }

    @Test
    void nullSelectionYieldsEmptyResult() {
        List<Document> documents = List.of(doc("A"));

        assertThat(guardrail.apply(documents, null, 10)).isEmpty();
    }

    private static Document doc(String sku) {
        return Document.builder().id(sku).text("text of " + sku)
            .metadata(java.util.Map.of("sku", sku))
            .build();
    }

    private static List<String> skus(List<Document> documents) {
        return documents.stream().map(document -> (String) document.getMetadata().get("sku")).toList();
    }
}
