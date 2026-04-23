package com.querymind.service;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.querymind.exception.OpenAIClientException;
import com.querymind.exception.SqlExecutionException;
import com.querymind.exception.UnsafeSqlException;
import com.querymind.model.dto.ChartData;
import com.querymind.model.dto.QueryResponse;
import com.querymind.model.entity.QueryHistory;
import com.querymind.repository.QueryHistoryRepository;
import com.querymind.service.ChartTypeInferenceService.ChartType;
import com.querymind.service.OpenAIClient.Message;
import com.querymind.service.PromptBuilderService.PromptPair;
import com.querymind.service.SqlExecutionService.ColumnMeta;
import com.querymind.service.SqlExecutionService.QueryResult;

@Service
public class QueryOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(QueryOrchestrationService.class);
    private static final int MAX_RETRIES = 2;

    private static final java.util.Set<Integer> NUMERIC_TYPES = java.util.Set.of(
            Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT,
            Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL
    );

    private final PromptBuilderService promptBuilderService;
    private final OpenAIClient openAIClient;
    private final SqlValidationService sqlValidationService;
    private final SqlExecutionService sqlExecutionService;
    private final ChartTypeInferenceService chartTypeInferenceService;
    private final QueryHistoryRepository queryHistoryRepository;
    private final ObjectMapper objectMapper;

    public QueryOrchestrationService(
            PromptBuilderService promptBuilderService,
            OpenAIClient openAIClient,
            SqlValidationService sqlValidationService,
            SqlExecutionService sqlExecutionService,
            ChartTypeInferenceService chartTypeInferenceService,
            QueryHistoryRepository queryHistoryRepository,
            ObjectMapper objectMapper) {
        this.promptBuilderService = promptBuilderService;
        this.openAIClient = openAIClient;
        this.sqlValidationService = sqlValidationService;
        this.sqlExecutionService = sqlExecutionService;
        this.chartTypeInferenceService = chartTypeInferenceService;
        this.queryHistoryRepository = queryHistoryRepository;
        this.objectMapper = objectMapper;
    }

    public QueryResponse executeQuery(String naturalLanguage) {
        // 1. Build prompt
        PromptPair prompt = promptBuilderService.buildPrompt(naturalLanguage);

        // 2. Call OpenAI
        String rawSql;
        try {
            rawSql = openAIClient.chatCompletion(prompt.systemPrompt(), prompt.userPrompt());
        } catch (OpenAIClientException e) {
            log.error("OpenAI call failed: {}", e.getMessage());
            persistFailure(naturalLanguage, null, e.getMessage());
            throw e;
        }

        // 3. Check for model-reported errors (e.g., "ERROR: cannot answer this question")
        if (rawSql.startsWith("ERROR:")) {
            String errorMsg = rawSql;
            persistFailure(naturalLanguage, rawSql, errorMsg);
            throw new SqlExecutionException(errorMsg);
        }

        // 4. Validate SQL
        String cleanedSql;
        try {
            cleanedSql = sqlValidationService.validate(rawSql);
        } catch (UnsafeSqlException e) {
            log.warn("Unsafe SQL from GPT-4o: {}", e.getMessage());
            persistFailure(naturalLanguage, rawSql, e.getMessage());
            throw e;
        }

        // 5. Execute with retry loop
        List<Message> conversationHistory = new ArrayList<>();
        conversationHistory.add(new Message("user", prompt.userPrompt()));
        conversationHistory.add(new Message("assistant", rawSql));

        return executeWithRetry(naturalLanguage, prompt.systemPrompt(), conversationHistory, cleanedSql, 0);
    }

    private QueryResponse executeWithRetry(
            String naturalLanguage,
            String systemPrompt,
            List<Message> conversationHistory,
            String cleanedSql,
            int attempt) {

        try {
            // Execute SQL
            QueryResult result = sqlExecutionService.execute(cleanedSql);

            // Infer chart type and build chart data
            ChartType chartType = chartTypeInferenceService.infer(result);
            String chartTypeStr = chartType.name().toLowerCase();
            ChartData chartData = buildChartData(chartType, result);

            // Persist (store first 5 rows for insight generation — DL-011)
            QueryHistory history = new QueryHistory(naturalLanguage, cleanedSql, chartTypeStr);
            history.setResultRowCount(result.rows().size());
            history.setExecutionTimeMs((int) result.executionTimeMs());
            history.setResultData(serializeRows(result.rows()));
            queryHistoryRepository.save(history);

            log.info("Query succeeded: {} rows in {} ms, chartType={} (attempt {})",
                    result.rows().size(), result.executionTimeMs(), chartTypeStr, attempt + 1);

            return new QueryResponse(
                    history.getId(),
                    naturalLanguage,
                    cleanedSql,
                    chartTypeStr,
                    chartData,
                    result.rows(),
                    result.rows().size(),
                    result.executionTimeMs(),
                    null
            );

        } catch (SqlExecutionException e) {
            if (attempt >= MAX_RETRIES) {
                log.warn("SQL execution failed after {} retries: {}", MAX_RETRIES, e.getMessage());
                persistFailure(naturalLanguage, cleanedSql, e.getMessage());
                throw e;
            }

            // Retry: feed error back to GPT-4o via conversation history
            log.info("SQL execution failed (attempt {}), retrying: {}", attempt + 1, e.getMessage());

            conversationHistory.add(new Message("user",
                    "This SQL failed with error: " + e.getMessage() + ". Fix it."));

            String retrySql;
            try {
                retrySql = openAIClient.chatCompletion(systemPrompt, conversationHistory);
            } catch (OpenAIClientException openAIEx) {
                persistFailure(naturalLanguage, cleanedSql, openAIEx.getMessage());
                throw openAIEx;
            }

            conversationHistory.add(new Message("assistant", retrySql));

            // Validate the retry SQL
            String retryCleanedSql;
            try {
                retryCleanedSql = sqlValidationService.validate(retrySql);
            } catch (UnsafeSqlException unsafeEx) {
                persistFailure(naturalLanguage, retrySql, unsafeEx.getMessage());
                throw unsafeEx;
            }

            return executeWithRetry(naturalLanguage, systemPrompt, conversationHistory, retryCleanedSql, attempt + 1);
        }
    }

    private ChartData buildChartData(ChartType chartType, QueryResult result) {
        List<ColumnMeta> columns = result.columns();
        List<Map<String, Object>> rows = result.rows();

        return switch (chartType) {
            case KPI -> {
                Map<String, Object> row = rows.get(0);
                List<ChartData.MetricValue> metrics = columns.stream()
                        .map(col -> new ChartData.MetricValue(col.name(), row.get(col.name())))
                        .toList();
                yield new ChartData(null, null, null, metrics);
            }
            case BAR, LINE -> {
                String xKey = columns.stream()
                        .filter(c -> !NUMERIC_TYPES.contains(c.jdbcType()))
                        .map(ColumnMeta::name)
                        .findFirst()
                        .orElse(columns.get(0).name());
                String yKey = columns.stream()
                        .filter(c -> NUMERIC_TYPES.contains(c.jdbcType()))
                        .map(ColumnMeta::name)
                        .findFirst()
                        .orElse(columns.get(columns.size() - 1).name());
                yield new ChartData(xKey, yKey, rows, null);
            }
            case TABLE -> null;
        };
    }

    private String serializeRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> subset = rows.subList(0, Math.min(5, rows.size()));
        try {
            return objectMapper.writeValueAsString(subset);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize result data: {}", e.getMessage());
            return null;
        }
    }

    private void persistFailure(String naturalLanguage, String sql, String errorMessage) {
        try {
            QueryHistory history = new QueryHistory(naturalLanguage, sql != null ? sql : "", "table");
            history.setErrorMessage(errorMessage);
            queryHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to persist error history: {}", e.getMessage());
        }
    }
}
