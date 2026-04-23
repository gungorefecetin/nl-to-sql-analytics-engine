package com.querymind.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseEnvelope<T>(
        boolean success,
        T data,
        ErrorBody error
) {
    public record ErrorBody(String code, String message, String timestamp) {}

    public static <T> ResponseEnvelope<T> success(T data) {
        return new ResponseEnvelope<>(true, data, null);
    }

    public static ResponseEnvelope<Void> error(String code, String message) {
        return new ResponseEnvelope<>(false, null,
                new ErrorBody(code, message, Instant.now().toString()));
    }
}
