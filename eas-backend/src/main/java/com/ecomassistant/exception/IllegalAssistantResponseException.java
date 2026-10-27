package com.ecomassistant.exception;

public class IllegalAssistantResponseException extends RuntimeException {
    private final String response   ;

    public IllegalAssistantResponseException(String message, String response) {
        super(message);
        this.response = response;
    }

    public String getResponse() {
        return response;
    }
}
