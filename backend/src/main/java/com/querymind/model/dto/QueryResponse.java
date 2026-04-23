package com.querymind.model.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryResponse(
        Long historyId,
        String naturalLanguage,
        String generatedSql,
        String chartType,
        ChartData chartData,
        List<Map<String, Object>> data,
        int rowCount,
        long executionTimeMs,
        String error
) {}
