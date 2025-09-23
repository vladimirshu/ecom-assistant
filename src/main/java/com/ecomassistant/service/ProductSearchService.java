package com.ecomassistant.service;

import com.ecomassistant.entity.Product;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);
    public static final int TOP_K = 10;
    private final VectorStore vectorStore;

    public ProductSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Search products using similarity search with optional sorting
     *
     * @param query The search query text
     * @param products Optional list of predefined products to search within (null for all products)
     * @param sortByPrice Optional sort parameter: "asc", "desc", or null for no sorting
     * @return List of matching products
     */
    public List<Product> searchProducts(String query, List<Product> products, String sortByPrice) {
        log.info("Searching products with query: '{}', predefined products count: {}, sort: {}",
                query, products != null ? products.size() : "all", sortByPrice);

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .similarityThreshold(0.25)
                .topK(TOP_K);

        // If specific products are provided, create a filter to search only among them
        if (CollectionUtils.isNotEmpty(products)) {
            List<String> skus = products.stream()
                    .map(Product::getSku)
                    .toList();
            Filter.Expression filterExpression = new FilterExpressionBuilder()
                    .in("sku", skus.toArray())
                    .build();
            requestBuilder.filterExpression(filterExpression);
        }

        // Execute similarity search
        List<Document> documents = vectorStore.similaritySearch(requestBuilder.build());

        // Convert documents to products
        List<Product> foundProducts = documents.stream()
                .map(this::documentToProduct)
                .collect(Collectors.toList());

        // Apply sorting if requested
        if (sortByPrice != null) {
            foundProducts = sortProducts(foundProducts, sortByPrice);
        }

        log.info("Found {} products matching the search criteria", foundProducts.size());
        return foundProducts;
    }

    /**
     * Search within predefined products only
     */
    public List<Product> searchWithinProducts(String query, List<Product> products) {
        return searchProducts(query, products, null);
    }

    /**
     * Search with sorting
     */
    public List<Product> searchProductsWithSort(String query, String sortByPrice) {
        return searchProducts(query, null, sortByPrice);
    }

    /**
     * Sort products by price
     */
    private List<Product> sortProducts(List<Product> products, String sortByPrice) {
        if ("asc".equalsIgnoreCase(sortByPrice)) {
            return products.stream()
                    .sorted(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
        }
        return products;
    }

    /**
     * Convert Document to Product entity
     */
    private Product documentToProduct(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        Product product = new Product();
        product.setSku((String) metadata.get("sku"));
        product.setColors((String) metadata.get("colors"));
        product.setSizes((String) metadata.get("sizes"));
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

