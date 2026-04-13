package com.querymind.exception;

/**
 * Thrown when AI-generated SQL fails security validation — blocked keywords,
 * non-SELECT statement, length overflow, etc.
 */
public class UnsafeSqlException extends RuntimeException {

    public UnsafeSqlException(String message) {
        super(message);
    }
}
