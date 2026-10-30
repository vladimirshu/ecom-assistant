package com.ecomassistant.catalog.quality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of running the offline quality gate: all findings plus the coverage facts the
 * gate measured. {@link #hasBlockingIssues()} is true when release must fail.
 */
public class QualityReport {

    private final List<QualityIssue> issues = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    private final Map<String, Integer> priceBandCounts = new LinkedHashMap<>();
    private final List<List<String>> duplicateDescriptionGroups = new ArrayList<>();
    private final List<List<String>> functionalDuplicateGroups = new ArrayList<>();
    private int productCount;

    public void add(QualityIssue issue) {
        issues.add(issue);
    }

    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }

    public void addCategoryCount(String category, int count) {
        categoryCounts.merge(category, count, Integer::sum);
    }

    public void addPriceBandCount(String band, int count) {
        priceBandCounts.merge(band, count, Integer::sum);
    }

    public void addDuplicateDescriptionGroup(List<String> skus) {
        duplicateDescriptionGroups.add(List.copyOf(skus));
    }

    public void addFunctionalDuplicateGroup(List<String> skus) {
        functionalDuplicateGroups.add(List.copyOf(skus));
    }

    public List<QualityIssue> issues() {
        return List.copyOf(issues);
    }

    public List<QualityIssue> blocking() {
        return issues.stream()
                .filter(issue -> issue.severity() == QualityIssue.Severity.BLOCKING)
                .toList();
    }

    public boolean hasBlockingIssues() {
        return issues.stream().anyMatch(issue -> issue.severity() == QualityIssue.Severity.BLOCKING);
    }

    public int productCount() {
        return productCount;
    }

    public Map<String, Integer> categoryCounts() {
        return Map.copyOf(categoryCounts);
    }

    public Map<String, Integer> priceBandCounts() {
        return Map.copyOf(priceBandCounts);
    }

    public List<List<String>> duplicateDescriptionGroups() {
        return List.copyOf(duplicateDescriptionGroups);
    }

    public List<List<String>> functionalDuplicateGroups() {
        return List.copyOf(functionalDuplicateGroups);
    }
}
