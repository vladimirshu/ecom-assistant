package com.ecomassistant.dto;

import com.ecomassistant.entity.Product;

import java.util.List;

public record ChatResponseDto(List<Product> products, List<String> suggestedPrompts) {
}