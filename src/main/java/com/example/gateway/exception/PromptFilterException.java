package com.example.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PromptFilterException extends ResponseStatusException {
    public PromptFilterException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
