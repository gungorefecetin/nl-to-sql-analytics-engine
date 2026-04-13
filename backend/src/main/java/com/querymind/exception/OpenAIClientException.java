package com.querymind.exception;

/**
 * Thrown when the OpenAI API call fails — network error, rate limit, invalid response, etc.
 * Orchestration layer catches this and translates to a user-friendly error.
 */
public class OpenAIClientException extends RuntimeException {

    public OpenAIClientException(String message) {
        super(message);
    }

    public OpenAIClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
