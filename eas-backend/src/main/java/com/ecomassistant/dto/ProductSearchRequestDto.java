package com.ecomassistant.dto;

import com.ecomassistant.validation.SafeSearchText;
import com.ecomassistant.validation.TextNormalizer;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequestDto {

    @SafeSearchText
    private String message;

    @NotBlank(message = "Conversation ID cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9\\-_]+$",
             message = "Conversation ID can only contain alphanumeric characters, hyphens, and underscores")
    @Size(min = 1, max = 50, message = "Conversation ID must be between 1 and 50 characters")
    private String conversationId = "1";

    private boolean clearMemory = false;

    public void setMessage(String message) {
        this.message = TextNormalizer.normalize(message);
    }
}
