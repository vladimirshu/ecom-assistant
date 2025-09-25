package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.entity.Product;
import com.ecomassistant.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImportService {

    private final VectorStore vectorStore;
    private final ProductRepository productRepository;

    private static final String CSV_FILE_PATH = "sampledata/apparel_jackets_sample.csv";


    public void importSampleProducts() {
        boolean isRelationalDBInitialized = productRepository.count() > 0;
        boolean isVectorStoreInitialized = isVectorStoreInitialized();
        if (isRelationalDBInitialized && isVectorStoreInitialized) {
            log.info("Products already imported. Skipping");
            return;
        }

        List<Product> products = extractProducts();
        if (!products.isEmpty()) {
            if (!isRelationalDBInitialized) {
                productRepository.saveAll(products);
            }
            if (!isVectorStoreInitialized) {
                initVectorStore(products);
            }
            log.info("Successfully imported {} products from CSV file", products.size());
        }
    }

    private List<Product> extractProducts() {
        ClassPathResource resource = new ClassPathResource(CSV_FILE_PATH);
        List<Product> products = new ArrayList<>();

        try (Reader reader = new InputStreamReader(
                resource.getInputStream()); CSVParser csvParser = CSVFormat.Builder.create().setHeader()
                .setSkipHeaderRecord(true).setDelimiter(";").build().parse(reader)) {

            for (CSVRecord csvRecord : csvParser) {
                Product product = parseProductFromRecord(csvRecord);
                if (product != null) {
                    products.add(product);
                }
            }
        } catch (IOException e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import products from CSV", e);
        } catch (Exception e) {
            log.error("Error importing products: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import products", e);
        }
        return products;
    }

    private boolean isVectorStoreInitialized() {
        try {
            var results = vectorStore.similaritySearch(SearchRequest.builder().query(" ").topK(1).build());
            if (results.isEmpty()) {
                log.info("Vector store is not initialized - no documents found");
                return false;
            } else {
                log.info("Vector store document: " + results.get(0).getMetadata().get("sku") + " - " + results.get(0)
                        .getText());
                return true;
            }
        } catch (Exception e) {
            log.error("Error checking if vector store is initialized", e);
            return false;
        }
    }


    private void initVectorStore(List<Product> products) {
        vectorStore.add(products.stream().map(VectorStoreUtils::productToDocument).toList());
    }



    private Product parseProductFromRecord(CSVRecord record) {
        try {
            if (record.size() < 5) {
                log.warn("Invalid CSV record format - expected 5 fields, got {}: {}", record.size(), record);
                return null;
            }

            String sku = record.get(0).trim();
            String colors = record.get(1).trim();
            String sizes = record.get(2).trim();
            String priceStr = record.get(3).trim();
            String description = record.get(4).trim();

            BigDecimal price = new BigDecimal(priceStr);

            return new Product(sku, colors, sizes, price, description);

        } catch (Exception e) {
            log.warn("Error parsing CSV record: {} - Error: {}", record, e.getMessage());
            return null;
        }
    }
}