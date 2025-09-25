package com.ecomassistant.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequestDto {

    @NotBlank(message = "Message cannot be blank")
    @Size(min = 3, max = 500, message = "Message must be between 3 and 500 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-_.,!?()<>€$£¥\\u00C0-\\u017F]+$",
             message = "Message contains invalid characters")
    private String message;

    @NotBlank(message = "Conversation ID cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9\\-_]+$", 
             message = "Conversation ID can only contain alphanumeric characters, hyphens, and underscores")
    @Size(min = 1, max = 50, message = "Conversation ID must be between 1 and 50 characters")
    @NotBlank(message = "Conversation ID cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9\\-_]+$", message = "Conversation ID can only contain alphanumeric characters, hyphens, and underscores")
    @Size(min = 1, max = 50, message = "Conversation ID must be between 1 and 50 characters")
    private String conversationId = "1";

    private boolean clearMemory = false;
}
