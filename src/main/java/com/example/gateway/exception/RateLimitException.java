package com.example.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RateLimitException extends ResponseStatusException {
    public RateLimitException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
