package com.ecomassistant.rag;

import com.ecomassistant.guardrails.PriceConstraint;
import com.ecomassistant.guardrails.PriceOperator;
import com.ecomassistant.retrieval.RetrievalContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductFilterQueryAugmenterTest {

    private final ProductFilterQueryAugmenter augmenter = new ProductFilterQueryAugmenter();

    @Test
    void rendersSearchQueryOriginalMessageNegativesAndPrice() {
        Query query = Query.builder()
            .text("blue cotton jacket")
            .context(Map.of(
                    ProductFilterContextKeys.ORIGINAL_MESSAGE, "blue cotton jacket under 200 euro, no wool",
                    ProductFilterContextKeys.PRICE_CONSTRAINT,
                    new PriceConstraint(PriceOperator.LESS_THAN, new BigDecimal("200")),
                    RetrievalContextKeys.NEGATIVE_EXAMPLES, List.of("wool")))
            .build();

        Query augmented = augmenter.augment(query, List.of(doc("JKT0001", "blue", "M", "120.00", "blue cotton jacket")));

        assertThat(augmented.text())
            .contains("blue cotton jacket")
            .contains("blue cotton jacket under 200 euro, no wool")
            .contains("wool")
            .contains("under 200 euro");
    }

    @Test
    void rendersOneLinePerCandidateWithSkuColorsSizesPriceAndDescription() {
        Query query = Query.builder()
            .text("rain jacket")
            .context(Map.of(
                    ProductFilterContextKeys.ORIGINAL_MESSAGE, "rain jacket",
                    RetrievalContextKeys.NEGATIVE_EXAMPLES, List.of()))
            .build();

        Query augmented = augmenter.augment(query, List.of(
                doc("JKT0001", "navy", "M", "120.00", "waterproof rain jacket"),
                doc("JKT0007", "red", "L", "80.58", "denim jacket")));

        assertThat(augmented.text())
            .contains("JKT0001 | navy | M | 120.00 | waterproof rain jacket")
            .contains("JKT0007 | red | L | 80.58 | denim jacket");
    }

    @Test
    void rendersPlaceholdersForMissingConstraints() {
        Query query = Query.builder()
            .text("rain jacket")
            .context(Map.of(ProductFilterContextKeys.ORIGINAL_MESSAGE, "rain jacket"))
            .build();

        Query augmented = augmenter.augment(query, List.of());

        assertThat(augmented.text())
            .contains("(none)");
    }

    private static Document doc(String sku, String colors, String sizes, String price, String description) {
        return Document.builder()
            .id(sku)
            .text(description)
            .metadata(Map.of(
                    "sku", sku,
                    "colors", colors,
                    "sizes", sizes,
                    "price", new BigDecimal(price),
                    "description", description))
            .build();
    }
}
