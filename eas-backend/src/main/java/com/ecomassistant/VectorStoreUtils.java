package com.ecomassistant;

import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.catalog.Product;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
public class VectorStoreUtils {
    private static final String CONTENT_FIELD_NAME = "doc_content";

    public static Document qdrantPointToDocument(Points.ScoredPoint point) {
        try {
            var id = point.getId().getUuid();

            var metadata = toObjectMap(point.getPayloadMap());
            metadata.put(DocumentMetadata.DISTANCE.value(), 1 - point.getScore());

            var content = (String) metadata.remove(CONTENT_FIELD_NAME);

            return Document.builder().id(id).text(content).metadata(metadata).score((double) point.getScore()).build();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> toObjectMap(Map<String, JsonWithInt.Value> payload) {
        Assert.notNull(payload, "Payload map must not be null");
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonWithInt.Value> entry : payload.entrySet()) {
            map.put(entry.getKey(), object(entry.getValue()));
        }
        return map;
    }

    private static Object object(JsonWithInt.Value value) {

        switch (value.getKindCase()) {
            case INTEGER_VALUE:
                return value.getIntegerValue();
            case STRING_VALUE:
                return value.getStringValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case LIST_VALUE:
                return object(value.getListValue());
            case STRUCT_VALUE:
                return toObjectMap(value.getStructValue().getFieldsMap());
            case NULL_VALUE:
                return null;
            case KIND_NOT_SET:
            default:
                log.warn("Unsupported value type: " + value.getKindCase());
                return null;
        }

    }

    private static Object object(JsonWithInt.ListValue listValue) {
        return listValue.getValuesList().stream().map(VectorStoreUtils::object).collect(Collectors.toList());
    }

    /**
     * Converts a product to a vector-store document. The document id is deterministic
     * ({@code UUID.nameUUIDFromBytes(sku)}) so re-importing a SKU overwrites the same
     * point instead of creating a duplicate.
     */
    public static Document productToDocument(Product product) {
        // Convert BigDecimal to Double for Qdrant compatibility
        Double price = product.getPrice() != null ? product.getPrice().doubleValue() : null;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sku", product.getSku());
        metadata.put("title", product.getTitle());
        metadata.put("category", product.getCategory() != null ? product.getCategory().slug() : null);
        metadata.put("colors", product.getColors());
        metadata.put("sizes", product.getSizes());
        metadata.put("price", price != null ? price : 0.0);
        metadata.put("description", product.getDescription());
        metadata.put("imageKey", product.getImageKey());

        // Searchable text: title, category name, colors, sizes and description. SKU and
        // price are omitted: they are noise for similarity, and price is handled by the
        // payload filter.
        String content = String.format("%s. %s. Colors: %s. Sizes: %s. Description: %s",
                nullToEmpty(product.getTitle()),
                product.getCategory() != null ? product.getCategory().displayName() : "",
                nullToEmpty(product.getColors()),
                nullToEmpty(product.getSizes()),
                nullToEmpty(product.getDescription()));

        String id = UUID.nameUUIDFromBytes(product.getSku().getBytes(StandardCharsets.UTF_8)).toString();

        return Document.builder().id(id).text(content).metadata(metadata).build();
    }

    /**
     * Convert Document to Product entity
     */
    public static Product documentToProduct(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        Product product = new Product();
        product.setSku((String) metadata.get("sku"));
        product.setTitle((String) metadata.get("title"));
        product.setCategory(ProductCategory.fromSlug((String) metadata.get("category")));
        product.setImageKey((String) metadata.get("imageKey"));
        product.setColors((String) metadata.get("colors"));
        product.setSizes((String) metadata.get("sizes"));
        product.setPrice(toBigDecimal(metadata.get("price")));
        product.setDescription((String) metadata.get("description"));

        return product;
    }

    private static BigDecimal toBigDecimal(Object priceObj) {
        if (priceObj == null) {
            return null;
        }
        if (priceObj instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (priceObj instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(priceObj.toString());
        }
        catch (NumberFormatException e) {
            log.warn("Could not parse price: {}", priceObj);
            return BigDecimal.ZERO;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
