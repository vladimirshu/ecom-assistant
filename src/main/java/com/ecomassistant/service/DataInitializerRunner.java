package com.ecomassistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializerRunner implements CommandLineRunner {

    private final ProductImportService productImportService;

    @Override
    public void run(String... args) {
        productImportService.importSampleProducts();
    }
}
