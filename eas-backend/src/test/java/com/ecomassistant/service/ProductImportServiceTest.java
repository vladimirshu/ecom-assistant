package com.ecomassistant.service;

import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.config.EcomImportProperties;
import com.ecomassistant.catalog.Product;
import io.qdrant.client.QdrantClient;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductImportServiceTest {

    private final ProductImportService service = new ProductImportService(
            mock(VectorStore.class),
            new EcomImportProperties("sampledata/apparel_jackets_sample.csv"),
            mock(QdrantClient.class));

    @Test
    void parsesValidRowWithDefaultCategoryImage() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;white,green;XS,S,L;66.93;;Classic bomber jacket with ribbed hems."));

        assertThat(product).isNotNull();
        assertThat(product.getSku()).isEqualTo("JKT0004");
        assertThat(product.getTitle()).isEqualTo("Classic Bomber Jacket");
        assertThat(product.getCategory()).isEqualTo(ProductCategory.BOMBER);
        assertThat(product.getColors()).isEqualTo("white,green");
        assertThat(product.getSizes()).isEqualTo("XS,S,L");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("66.93"));
        assertThat(product.getImageKey()).isEqualTo("categories/bomber.webp");
        assertThat(product.getDescription()).isEqualTo("Classic bomber jacket with ribbed hems.");
    }

    @Test
    void missingTitleIsSkipped() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;;bomber;white,green;XS,S,L;66.93;;Classic bomber jacket."));

        assertThat(product).isNull();
    }

    @Test
    void unknownCategoryIsSkipped() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Hoodie;hoodie;white,green;XS,S,L;66.93;;A hoodie."));

        assertThat(product).isNull();
    }

    @Test
    void zeroOrNegativePriceIsSkipped() {
        assertThat(service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;white;XS;0;;Classic bomber jacket."))).isNull();
        assertThat(service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;white;XS;-5.00;;Classic bomber jacket."))).isNull();
    }

    @Test
    void titleLongerThan80CharactersIsSkipped() {
        String longTitle = "T".repeat(81);

        assertThat(service.parseProductFromRecord(record(
                "JKT0004;" + longTitle + ";bomber;white;XS;66.93;;Classic bomber jacket."))).isNull();
        assertThat(service.parseProductFromRecord(record(
                "JKT0004;" + "T".repeat(80) + ";bomber;white;XS;66.93;;Classic bomber jacket."))).isNotNull();
    }

    @Test
    void rowWithTooFewColumnsIsSkippedInsteadOfThrowing() {
        Product product = service.parseProductFromRecord(record("JKT0004;Classic Bomber Jacket;bomber;white"));

        assertThat(product).isNull();
    }

    @Test
    void explicitImageIsUsedUnchanged() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;white,green;XS,S,L;66.93;products/JKT0004/front.webp;Classic bomber jacket."));

        assertThat(product).isNotNull();
        assertThat(product.getImageKey()).isEqualTo("products/JKT0004/front.webp");
    }

    private static CSVRecord record(String line) {
        CSVFormat format = CSVFormat.Builder.create()
                .setHeader("sku", "title", "category", "colors", "sizes", "price", "image", "description")
                .setDelimiter(";")
                .build();
        try (CSVParser parser = format.parse(new StringReader(line))) {
            return parser.getRecords().get(0);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
