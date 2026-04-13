package com.querymind.service;

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
     * @return result containing rows and elapsed time in milliseconds
     * @throws SqlExecutionException on any failure — includes PG error message for retry loop
     */
    public Result execute(String sql) {
        log.debug("Executing SQL ({} chars)", sql.length());
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> rows = readonlyJdbcTemplate.queryForList(sql);
            long elapsedMs = System.currentTimeMillis() - start;
            log.debug("SQL executed: {} rows in {} ms", rows.size(), elapsedMs);
            return new Result(rows, elapsedMs);
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

    public record Result(List<Map<String, Object>> rows, long executionTimeMs) {}
}
