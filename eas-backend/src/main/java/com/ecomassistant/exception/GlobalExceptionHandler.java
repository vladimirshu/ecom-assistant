package com.ecomassistant.exception;

import com.ecomassistant.dto.ChatResponseDto;
import com.ecomassistant.guardrails.GuardrailRejectedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation error: {}", errors);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid argument");
        error.put("message", ex.getMessage());
        log.warn("Illegal argument: {}", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalAssistantResponseException.class)
    public ResponseEntity<Map<String, String>> handleIllegalAssistantResponseException(IllegalAssistantResponseException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Illegal assistant response");
        error.put("message", ex.getMessage());
        log.error("Illegal assistant response: {}: Response: {}", ex.getMessage(), ex.getResponse());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(GuardrailRejectedException.class)
    public ResponseEntity<ChatResponseDto> handleGuardrailRejectedException(GuardrailRejectedException ex) {
        log.info("Request rejected by query analysis: {}", ex.getQueryAnalysis().rejectionReasons());
        return new ResponseEntity<>(
                new ChatResponseDto(null, null, ex.getQueryAnalysis().suggestedPrompts()),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
