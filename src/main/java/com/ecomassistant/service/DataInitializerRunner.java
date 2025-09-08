package com.ecomassistant.service;

import com.ecomassistant.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializerRunner implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductImportService productImportService;

    @Override
    public void run(String... args) {
        boolean isProductsEmpty = productRepository.count() == 0;
        if (isProductsEmpty) {
            productImportService.importSampleProducts();
        } else {
            log.info("Products table already contains data. Skipping CSV import.");
        }
    }
}
