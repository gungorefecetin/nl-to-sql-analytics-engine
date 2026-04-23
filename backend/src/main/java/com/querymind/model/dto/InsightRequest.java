package com.querymind.model.dto;

import jakarta.validation.constraints.NotNull;

public record InsightRequest(
        @NotNull Long historyId
) {}
