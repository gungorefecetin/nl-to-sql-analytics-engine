package com.querymind.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(max = 500, message = "Question must be at most 500 characters")
        String naturalLanguage
) {}
