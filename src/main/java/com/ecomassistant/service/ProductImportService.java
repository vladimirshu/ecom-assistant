package com.ecomassistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.ecomassistant.entity.Product;
import com.ecomassistant.repository.ProductRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImportService {

    private final ProductRepository productRepository;
    private static final String CSV_FILE_PATH = "sampledata/apparel_seed_100.csv";

    public void importSampleProducts() {
        try {
            ClassPathResource resource = new ClassPathResource(CSV_FILE_PATH);
            List<Product> products = new ArrayList<>();

            try (Reader reader = new InputStreamReader(resource.getInputStream());
                 CSVParser csvParser = CSVFormat.Builder.create().setSkipHeaderRecord(true).build()
                   .parse(reader)) {

                for (CSVRecord csvRecord : csvParser) {
                    Product product = parseProductFromRecord(csvRecord);
                    if (product != null) {
                        products.add(product);
                    }
                }
            }

            if (!products.isEmpty()) {
                productRepository.saveAll(products);
                log.info("Successfully imported {} products from CSV file", products.size());
            } else {
                log.warn("No valid products found in CSV file");
            }

        } catch (IOException e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import products from CSV", e);
        } catch (Exception e) {
            log.error("Error importing products: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import products", e);
        }
    }

    private Product parseProductFromRecord(CSVRecord record) {
        try {
            if (record.size() < 5) {
                log.warn("Invalid CSV record format - expected 5 fields, got {}: {}", record.size(), record);
                return null;
            }

            String sku = record.get(0).trim();
            String color = record.get(1).trim();
            String size = record.get(2).trim();
            String priceStr = record.get(3).trim();
            String description = record.get(4).trim();

            BigDecimal price = new BigDecimal(priceStr);

            return new Product(sku, color, size, price, description);

        } catch (Exception e) {
            log.warn("Error parsing CSV record: {} - Error: {}", record, e.getMessage());
            return null;
        }
    }
}
