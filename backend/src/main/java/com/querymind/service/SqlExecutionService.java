package com.querymind.service;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.querymind.exception.SqlExecutionException;

@Service
public class SqlExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionService.class);

    private final JdbcTemplate readonlyJdbcTemplate;

    public SqlExecutionService(
            @Qualifier("readonlyJdbcTemplate") JdbcTemplate readonlyJdbcTemplate,
            @Value("${querymind.max-execution-time-seconds:30}") int queryTimeoutSeconds) {
        this.readonlyJdbcTemplate = readonlyJdbcTemplate;
        this.readonlyJdbcTemplate.setQueryTimeout(queryTimeoutSeconds);
    }

    /**
     * Executes validated SQL on the read-only datasource.
     *
     * @param sql validated SQL (must have passed SqlValidationService first)
     * @return result containing column metadata, rows, and elapsed time
     * @throws SqlExecutionException on any failure — includes PG error message for retry loop
     */
    public QueryResult execute(String sql) {
        log.debug("Executing SQL ({} chars)", sql.length());
        long start = System.currentTimeMillis();
        try {
            QueryResult result = readonlyJdbcTemplate.query(sql, rs -> {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                // Extract column metadata once
                List<ColumnMeta> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(new ColumnMeta(meta.getColumnLabel(i), meta.getColumnType(i)));
                }

                // Iterate rows
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(columns.get(i - 1).name(), rs.getObject(i));
                    }
                    rows.add(row);
                }

                return new QueryResult(columns, rows);
            });
            long elapsedMs = System.currentTimeMillis() - start;
            log.debug("SQL executed: {} rows, {} columns in {} ms",
                    result.rows().size(), result.columns().size(), elapsedMs);
            return new QueryResult(result.columns(), result.rows(), elapsedMs);
        } catch (DataAccessException e) {
            long elapsedMs = System.currentTimeMillis() - start;
            String pgError = extractMessage(e);
            log.warn("SQL execution failed after {} ms: {}", elapsedMs, pgError);
            throw new SqlExecutionException(pgError, e);
        }
    }

    private String extractMessage(DataAccessException e) {
        // Walk the cause chain to find the most specific PG error message
        Throwable cause = e.getMostSpecificCause();
        String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return e.getMessage();
    }

    public record ColumnMeta(String name, int jdbcType) {}

    public record QueryResult(List<ColumnMeta> columns, List<Map<String, Object>> rows, long executionTimeMs) {
        /** Used by ResultSetExtractor lambda (before timing is known). */
        QueryResult(List<ColumnMeta> columns, List<Map<String, Object>> rows) {
            this(columns, rows, 0);
        }
    }
}
