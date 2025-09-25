package com.ecomassistant;

import com.ecomassistant.entity.Product;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.util.Assert;

import java.math.BigDecimal;
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


    public static Document productToDocument(Product product) {
        // Convert BigDecimal to Double for Qdrant compatibility
        Double price = product.getPrice() != null ? product.getPrice().doubleValue() : null;

        Map<String, Object> metadata = Map.of(
                "sku", product.getSku(),
                "colors", product.getColors(),
                "sizes", product.getSizes(),
                "price", price != null ? price : 0.0,
                "description", product.getDescription()
        );

        // Create searchable text content
        String content = String.format("SKU: %s, Colors: %s, Sizes: %s, Price: %.2f, Description: %s",
                product.getSku(),
                product.getColors(),
                product.getSizes(),
                price != null ? price : 0.0,
                product.getDescription()
        );

        return new Document(content, metadata);
    }

    /**
     * Convert Document to Product entity
     */
    public static Product documentToProduct(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        Product product = new Product();
        product.setSku((String) metadata.get("sku"));
        product.setColors((String) metadata.get("colors"));
        product.setSizes((String) metadata.get("sizes"));
        product.setPrice(BigDecimal.valueOf((Double) metadata.get("price")));
        product.setDescription(document.getText());

        // Handle price conversion
        Object priceObj = metadata.get("price");
        if (priceObj != null) {
            if (priceObj instanceof BigDecimal) {
                product.setPrice((BigDecimal) priceObj);
            } else if (priceObj instanceof Number) {
                product.setPrice(new BigDecimal(priceObj.toString()));
            } else {
                try {
                    product.setPrice(new BigDecimal(priceObj.toString()));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse price: {}", priceObj);
                    product.setPrice(BigDecimal.ZERO);
                }
            }
        }

        return product;
    }
}
