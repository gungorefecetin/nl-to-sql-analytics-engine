package com.querymind.service;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.querymind.service.SqlExecutionService.ColumnMeta;
import com.querymind.service.SqlExecutionService.QueryResult;

@Service
public class ChartTypeInferenceService {

    private static final Set<Integer> NUMERIC_TYPES = Set.of(
            Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT,
            Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL
    );

    private static final Set<Integer> TEMPORAL_TYPES = Set.of(
            Types.DATE, Types.TIME, Types.TIMESTAMP,
            Types.TIMESTAMP_WITH_TIMEZONE, Types.TIME_WITH_TIMEZONE
    );

    private static final Pattern TEMPORAL_NAME_PATTERN = Pattern.compile(
            "(?i)(month|date|year|week|quarter|period|day)"
    );

    /**
     * Infers chart type from result shape using deterministic rules.
     * <p>
     * Decision order:
     * 1. Empty result → TABLE
     * 2. Single row with ≤3 columns, at least one numeric → KPI
     * 3. Temporal column (by JDBC type or name pattern) + numeric → LINE
     * 4. One string column + one numeric column, ≤20 rows → BAR
     * 5. Fallback → TABLE
     * <p>
     * PIE is deliberately excluded from automatic inference (MVP scope).
     */
    public ChartType infer(QueryResult result) {
        List<ColumnMeta> columns = result.columns();
        List<Map<String, Object>> rows = result.rows();

        if (rows.isEmpty() || columns.isEmpty()) {
            return ChartType.TABLE;
        }

        long numericCount = columns.stream().filter(c -> isNumeric(c.jdbcType())).count();
        long stringCount = columns.stream().filter(c -> isString(c.jdbcType())).count();
        boolean hasTemporal = columns.stream().anyMatch(this::isTemporal);

        // KPI: single row, at least one numeric, few columns
        if (rows.size() == 1 && numericCount >= 1 && columns.size() <= 3) {
            return ChartType.KPI;
        }

        // LINE: temporal column + at least one numeric
        if (hasTemporal && numericCount >= 1) {
            return ChartType.LINE;
        }

        // BAR: few columns (label + values), at least one string + one numeric, ≤20 rows
        if (columns.size() <= 3 && stringCount >= 1 && numericCount >= 1 && rows.size() <= 20) {
            return ChartType.BAR;
        }

        return ChartType.TABLE;
    }

    private boolean isNumeric(int jdbcType) {
        return NUMERIC_TYPES.contains(jdbcType);
    }

    private boolean isString(int jdbcType) {
        return jdbcType == Types.VARCHAR || jdbcType == Types.CHAR
                || jdbcType == Types.LONGVARCHAR || jdbcType == Types.NVARCHAR
                || jdbcType == Types.NCHAR || jdbcType == Types.LONGNVARCHAR;
    }

    private boolean isTemporal(ColumnMeta column) {
        if (TEMPORAL_TYPES.contains(column.jdbcType())) {
            return true;
        }
        // Check column name for temporal patterns (handles "month" as VARCHAR, etc.)
        return TEMPORAL_NAME_PATTERN.matcher(column.name()).find();
    }

    public enum ChartType {
        BAR, LINE, KPI, TABLE
    }
}
