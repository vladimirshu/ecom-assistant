package com.ecomassistant.tools;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class AssistantTools {
    private static final Logger logger = LoggerFactory.getLogger(AssistantTools.class);


    @Tool(description = """
                        Processes not relevant to the apparel search part of the prompt. 
                        Call this function if some parts of user's message are not related to the apparel search""")
    public String passNotRelevantContent(
            @ToolParam(description = "not relevant pieces of content") List<String> notRelevantContentPieces,
            ToolContext toolContext) {
        logger.info("Passing not relevant content pieces: {}", notRelevantContentPieces);
        if (CollectionUtils.isEmpty(notRelevantContentPieces) || notRelevantContentPieces.stream().anyMatch(StringUtils::isBlank)) {
            return "";
        }
        AssistantToolsCallback toolCallback = getAssistantToolCallback(toolContext);
        toolCallback.setValidPrompt(false);
        toolCallback.addSuggestedPrompt(
                "Consider removing not relevant content: " + String.join("; ", notRelevantContentPieces) + ". ");
        return "";
    }

    private static AssistantToolsCallback getAssistantToolCallback(ToolContext toolContext) {
        return (AssistantToolsCallback) toolContext.getContext().get(AssistantToolsCallback.class.getName());
    }

    @Tool(description = """
                        suggests a user to update their message so it's clear and fully understandable in the context of apparel search.
                        Call this function if user's message is ambiguous or confusing or need clarification or may produce lost in the middle problem.""")
    public String updateConfusingPrompt(
            @ToolParam(description = "new prompt (message template). Pass here a new recommended prompt or message template. Or explanation what has to be improved in the prompt") String suggestedPrompt,
            ToolContext toolContext) {
        logger.info("Suggesting user prompt: {}", suggestedPrompt);
        if (StringUtils.isBlank(suggestedPrompt)) {
            return "";
        }
        AssistantToolsCallback toolCallback = getAssistantToolCallback(toolContext);
        toolCallback.setValidPrompt(false);
        toolCallback.addSuggestedPrompt("Please rephrase your prompt for better results: " + suggestedPrompt);
        return "";
    }

    /**
     * Here we only need to mark the tool as been called. Actual invocation will be done manually when user prompt is validated.
     */
    @Tool(description = "Filters products based on a given price and comparison operator")
    public String filterProductsByPrice(@ToolParam(description = "numerical price value") String price,
                                        @ToolParam(description = "price comparison operator") PriceOperator operator,
                                        ToolContext toolContext) {
        logger.info("Filtering items with price {} {}", operator, price);
        if (StringUtils.isBlank(price) || operator == null || PriceOperator.NOT_RELEVANT.equals(operator)) {
            return "";
        }
        AssistantToolsCallback toolCallback = getAssistantToolCallback(toolContext);
        if (PriceOperator.LOWEST.equals(operator)) {
            toolCallback.setSortByPriceAsc(true);
        } else {
            toolCallback.setFilterByPrice(true);
            toolCallback.setFilterProductsByPriceValue(price);
            toolCallback.setFilterProductsByPriceOperator(operator);
        }
        return "";
    }

    @Tool(description = "accepts parts of the user's input that are negatives (negative examples) to exclude them from the vector store similarity search")
    public String excludeNegativeExamples(@ToolParam(description = "negative examples to exclude from the vector store similarity search") List<String> negativeExamples,
                                          ToolContext toolContext) {
        logger.info("Excluding negative examples: {}", negativeExamples);
        if (CollectionUtils.isEmpty(negativeExamples) || negativeExamples.stream().anyMatch(StringUtils::isBlank)) {
            return "";
        }
        AssistantToolsCallback toolCallback = getAssistantToolCallback(toolContext);
        toolCallback.addNegativeExamples(negativeExamples);
        return "";
    }
}
