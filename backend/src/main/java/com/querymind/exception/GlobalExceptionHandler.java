package com.querymind.exception;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.querymind.model.dto.ResponseEnvelope;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnsafeSqlException.class)
    public ResponseEntity<ResponseEnvelope<Void>> handleUnsafeSql(UnsafeSqlException e) {
        return ResponseEntity.badRequest()
                .body(ResponseEnvelope.error("UNSAFE_SQL", "This type of query is not allowed."));
    }

    @ExceptionHandler(SqlExecutionException.class)
    public ResponseEntity<ResponseEnvelope<Void>> handleSqlExecution(SqlExecutionException e) {
        // Soft error per PRD — 200 status, error in envelope
        return ResponseEntity.ok(
                ResponseEnvelope.error("SQL_EXECUTION_ERROR",
                        "Could not generate a valid query for this question. Try rephrasing."));
    }

    @ExceptionHandler(OpenAIClientException.class)
    public ResponseEntity<ResponseEnvelope<Void>> handleOpenAIError(OpenAIClientException e) {
        log.error("OpenAI API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ResponseEnvelope.error("AI_SERVICE_ERROR",
                        "AI service temporarily unavailable. Please try again."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseEnvelope<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return ResponseEntity.badRequest()
                .body(ResponseEnvelope.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler({NoSuchElementException.class})
    public ResponseEntity<ResponseEnvelope<Void>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResponseEnvelope.error("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ResponseEnvelope<Void>> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ResponseEnvelope.error("RATE_LIMIT_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseEnvelope<Void>> handleResponseStatus(ResponseStatusException e) {
        String code = e.getStatusCode().value() == 404 ? "NOT_FOUND" : "BAD_REQUEST";
        return ResponseEntity.status(e.getStatusCode())
                .body(ResponseEnvelope.error(code, e.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseEnvelope<Void>> handleGeneric(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseEnvelope.error("INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again."));
    }
}
