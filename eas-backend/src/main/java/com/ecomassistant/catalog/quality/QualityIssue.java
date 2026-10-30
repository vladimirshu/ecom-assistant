package com.ecomassistant.catalog.quality;

/**
 * A single finding from the offline quality gate. {@code BLOCKING} issues must be resolved
 * before the dataset can be published; {@code REVIEW} issues require human/agent review
 * (for example near-duplicate wording or a soft factual inconsistency) but do not by
 * themselves fail the release gate.
 */
public record QualityIssue(Severity severity, String check, String sku, String message) {

    public enum Severity {
        BLOCKING,
        REVIEW
    }
}
