package com.querymind.model.dto;

import java.util.List;
import java.util.Map;

public record QueryResponse(
        Long historyId,
        String naturalLanguage,
        String generatedSql,
        List<Map<String, Object>> data,
        int rowCount,
        long executionTimeMs,
        String error
) {}
