package com.querymind.model.dto;

import java.time.LocalDateTime;

public record QueryHistoryResponse(
        Long id,
        String naturalLanguage,
        String chartType,
        Integer rowCount,
        Integer executionTimeMs,
        LocalDateTime createdAt
) {}
