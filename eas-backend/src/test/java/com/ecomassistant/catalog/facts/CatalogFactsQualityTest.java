package com.ecomassistant.catalog.facts;

import com.ecomassistant.catalog.CatalogCategoryRegistry;
import com.ecomassistant.catalog.quality.CatalogQualityGate;
import com.ecomassistant.catalog.quality.QualityIssue;
import com.ecomassistant.catalog.quality.QualityReport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline seed quality gate and CSV-export parity. These checks require no model, vector
 * store or network access: they validate the authored facts source against its own
 * contract and against the committed import CSV.
 */
class CatalogFactsQualityTest {

    private static final List<String> PARTS = List.of(
            "sampledata/facts/men-jackets-coats.json",
            "sampledata/facts/men-knitwear.json",
            "sampledata/facts/men-bottoms.json",
            "sampledata/facts/women-jackets-coats.json",
            "sampledata/facts/women-knitwear.json",
            "sampledata/facts/women-bottoms.json");

    @Test
    void catalogHas200DistinctProducts() {
        CatalogFacts facts = CatalogFactsLoader.loadAllClasspath(PARTS);
        assertThat(facts.products()).hasSize(200);
    }

    @Test
    void qualityGateHasNoBlockingIssues() {
        CatalogFacts facts = CatalogFactsLoader.loadAllClasspath(PARTS);
        CatalogQualityGate gate = new CatalogQualityGate(
                new CatalogCategoryRegistry("catalog/browsing-categories.json"));
        QualityReport report = gate.audit(facts);

        for (QualityIssue issue : report.blocking()) {
            System.out.println("BLOCKING " + issue);
        }
        assertThat(report.blocking()).isEmpty();
    }

    @Test
    void csvExportMatchesCommittedFile() throws Exception {
        CatalogFacts facts = CatalogFactsLoader.loadAllClasspath(PARTS);
        String exported = CatalogFactsExporter.toCsv(facts);

        String committed;
        try (var in = new ClassPathResource("sampledata/apparel_catalog.csv").getInputStream()) {
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(exported).isEqualTo(committed);
    }
}
