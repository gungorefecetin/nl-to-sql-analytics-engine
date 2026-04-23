package com.querymind.model.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChartData(
        String xKey,
        String yKey,
        List<Map<String, Object>> rows,
        List<MetricValue> metrics
) {
    public record MetricValue(String label, Object value) {}
}
