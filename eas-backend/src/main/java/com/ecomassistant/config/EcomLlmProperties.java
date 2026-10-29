package com.ecomassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-search LLM cost bounds. A cap that is too low truncates the model's JSON and turns
 * into a schema failure, so the defaults leave a margin over the typical output size.
 *
 * @param queryAnalysisMaxOutputTokens maximum output tokens for the query-analysis call
 * @param productFilterMaxOutputTokens maximum output tokens for the product-filtering call
 */
@ConfigurationProperties(prefix = "ecom.llm")
public record EcomLlmProperties(
        @DefaultValue("500") int queryAnalysisMaxOutputTokens,
        @DefaultValue("300") int productFilterMaxOutputTokens) {
}
