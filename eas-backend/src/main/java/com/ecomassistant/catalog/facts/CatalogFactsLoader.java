package com.ecomassistant.catalog.facts;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the versioned authoring-facts document from a classpath resource or a filesystem
 * path. The facts document is the source of truth for the catalog; the import CSV is an
 * export of it, verified for parity.
 */
public final class CatalogFactsLoader {

    /** The ordered authoring-facts part files that make up the released catalog. */
    public static final List<String> AUTHORING_PARTS = List.of(
            "sampledata/facts/men-jackets-coats.json",
            "sampledata/facts/men-knitwear.json",
            "sampledata/facts/men-bottoms.json",
            "sampledata/facts/women-jackets-coats.json",
            "sampledata/facts/women-knitwear.json",
            "sampledata/facts/women-bottoms.json");

    private CatalogFactsLoader() {
    }

    public static CatalogFacts loadClasspath(String resource) {
        try (var in = new ClassPathResource(resource).getInputStream()) {
            return mapper().readValue(in, CatalogFacts.class);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read catalog facts from " + resource, e);
        }
    }

    public static CatalogFacts load(Path file) {
        try (var in = Files.newInputStream(file)) {
            return mapper().readValue(in, CatalogFacts.class);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read catalog facts from " + file, e);
        }
    }

    /**
     * Loads several authoring-facts part files and merges them into one logical catalog.
     * The facts source is split across part files for authoring convenience; the merged
     * view is what the exporter, quality gate and benchmark consume. Part files must agree
     * on {@code schemaVersion} and {@code catalogId}.
     */
    public static CatalogFacts loadAllClasspath(List<String> resources) {
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("No catalog facts resources provided");
        }
        int schemaVersion = -1;
        String catalogId = null;
        Map<String, Object> provenance = new LinkedHashMap<>();
        List<FactProduct> products = new ArrayList<>();
        for (String resource : resources) {
            CatalogFacts part = loadClasspath(resource);
            if (schemaVersion == -1) {
                schemaVersion = part.schemaVersion();
                catalogId = part.catalogId();
            }
            else if (schemaVersion != part.schemaVersion() || !java.util.Objects.equals(catalogId, part.catalogId())) {
                throw new IllegalArgumentException("Catalog facts parts disagree on schema/version or catalog id: "
                        + resource);
            }
            if (part.provenance() != null) {
                provenance.putAll(part.provenance());
            }
            products.addAll(part.products());
        }
        return new CatalogFacts(schemaVersion, catalogId, provenance, products);
    }

    private static JsonMapper mapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
