package com.querymind.exception;

/**
 * Thrown when AI-generated SQL fails to execute against PostgreSQL.
 * Message includes the sanitized PG error so the retry loop can feed it back to GPT-4o.
 */
public class SqlExecutionException extends RuntimeException {

    public SqlExecutionException(String message) {
        super(message);
    }

    public SqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
