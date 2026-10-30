package com.ecomassistant.service;

import com.ecomassistant.catalog.CatalogCategoryRegistry;
import com.ecomassistant.catalog.Product;
import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.config.EcomImportProperties;
import com.ecomassistant.listing.CatalogReadiness;
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
            new EcomImportProperties("sampledata/apparel_catalog_sample.csv"),
            mock(QdrantClient.class),
            new CatalogCategoryRegistry("catalog/browsing-categories.json"),
            new CatalogReadiness());

    @Test
    void parsesValidRowWithDefaultCategoryImage() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;men-bomber;white,green;XS,S,L;66.93;;Classic bomber jacket with ribbed hems."));

        assertThat(product).isNotNull();
        assertThat(product.getSku()).isEqualTo("JKT0004");
        assertThat(product.getTitle()).isEqualTo("Classic Bomber Jacket");
        assertThat(product.getCategory()).isEqualTo(ProductCategory.BOMBER);
        assertThat(product.getColors()).isEqualTo("white,green");
        assertThat(product.getSizes()).isEqualTo("XS,S,L");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("66.93"));
        assertThat(product.getImageKey()).isEqualTo("categories/bomber.webp");
        assertThat(product.getDescription()).isEqualTo("Classic bomber jacket with ribbed hems.");
        assertThat(product.getCategoryIds()).containsExactly("men-bomber");
        assertThat(product.getCategoryPathIds())
                .contains("men-bomber", "men-jackets", "men-jackets-coats", "men");
        assertThat(product.getBrowsingText()).isEqualTo("Men Bomber Jackets");
    }

    @Test
    void multipleDirectMembershipsExpandToUnionOfPaths() {
        Product product = service.parseProductFromRecord(record(
                "U-HOODIE;Unisex Hoodie;hoodie;men-sweaters,women-sweaters;grey;M;95.00;;A shared hoodie."));

        assertThat(product).isNotNull();
        assertThat(product.getCategoryIds()).containsExactly("men-sweaters", "women-sweaters");
        assertThat(product.getCategoryPathIds())
                .contains("men", "men-sweaters", "women", "women-sweaters");
    }

    @Test
    void missingTitleIsSkipped() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;;bomber;men-bomber;white,green;XS,S,L;66.93;;Classic bomber jacket."));

        assertThat(product).isNull();
    }

    @Test
    void unknownGarmentStyleIsSkipped() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Hoodie;tshirt;men-sweaters;white,green;XS,S,L;66.93;;A shirt."));

        assertThat(product).isNull();
    }

    @Test
    void missingBrowsingCategoryIdsIsSkipped() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;;white,green;XS,S,L;66.93;;Classic bomber jacket."));

        assertThat(product).isNull();
    }

    @Test
    void unknownBrowsingCategoryIdIsSkipped() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;unknown-branch;white,green;XS,S,L;66.93;;Classic bomber jacket."));

        assertThat(product).isNull();
    }

    @Test
    void zeroOrNegativePriceIsSkipped() {
        assertThat(service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;men-bomber;white;XS;0;;Classic bomber jacket."))).isNull();
        assertThat(service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;men-bomber;white;XS;-5.00;;Classic bomber jacket."))).isNull();
    }

    @Test
    void titleLongerThan80CharactersIsSkipped() {
        String longTitle = "T".repeat(81);

        assertThat(service.parseProductFromRecord(record(
                "JKT0004;" + longTitle + ";bomber;men-bomber;white;XS;66.93;;Classic bomber jacket."))).isNull();
        assertThat(service.parseProductFromRecord(record(
                "JKT0004;" + "T".repeat(80) + ";bomber;men-bomber;white;XS;66.93;;Classic bomber jacket."))).isNotNull();
    }

    @Test
    void rowWithTooFewColumnsIsSkippedInsteadOfThrowing() {
        Product product = service.parseProductFromRecord(record("JKT0004;Classic Bomber Jacket;bomber;men-bomber;white"));

        assertThat(product).isNull();
    }

    @Test
    void oldEightColumnRowIsSkippedRequiringUpgrade() {
        CSVFormat format = CSVFormat.Builder.create()
                .setHeader("sku", "title", "category", "colors", "sizes", "price", "image", "description")
                .setDelimiter(";")
                .build();
        CSVRecord record = record("JKT0004;Classic Bomber Jacket;bomber;white,green;XS,S,L;66.93;;Classic bomber jacket.",
                format);

        assertThat(service.parseProductFromRecord(record)).isNull();
    }

    @Test
    void explicitImageIsUsedUnchanged() {
        Product product = service.parseProductFromRecord(record(
                "JKT0004;Classic Bomber Jacket;bomber;men-bomber;white,green;XS,S,L;66.93;products/JKT0004/front.webp;Classic bomber jacket."));

        assertThat(product).isNotNull();
        assertThat(product.getImageKey()).isEqualTo("products/JKT0004/front.webp");
    }

    private static CSVRecord record(String line) {
        return record(line, CSVFormat.Builder.create()
                .setHeader("sku", "title", "category", "categoryIds", "colors", "sizes", "price", "image",
                        "description")
                .setDelimiter(";")
                .build());
    }

    private static CSVRecord record(String line, CSVFormat format) {
        try (CSVParser parser = format.parse(new StringReader(line))) {
            return parser.getRecords().get(0);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
