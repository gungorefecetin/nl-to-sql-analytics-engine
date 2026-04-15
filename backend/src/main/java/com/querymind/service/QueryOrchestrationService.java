package com.querymind.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.querymind.exception.OpenAIClientException;
import com.querymind.exception.SqlExecutionException;
import com.querymind.exception.UnsafeSqlException;
import com.querymind.model.dto.QueryResponse;
import com.querymind.model.entity.QueryHistory;
import com.querymind.repository.QueryHistoryRepository;
import com.querymind.service.OpenAIClient.Message;
import com.querymind.service.PromptBuilderService.PromptPair;
import com.querymind.service.SqlExecutionService.Result;

@Service
public class QueryOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(QueryOrchestrationService.class);
    private static final int MAX_RETRIES = 2;

    private final PromptBuilderService promptBuilderService;
    private final OpenAIClient openAIClient;
    private final SqlValidationService sqlValidationService;
    private final SqlExecutionService sqlExecutionService;
    private final QueryHistoryRepository queryHistoryRepository;

    public QueryOrchestrationService(
            PromptBuilderService promptBuilderService,
            OpenAIClient openAIClient,
            SqlValidationService sqlValidationService,
            SqlExecutionService sqlExecutionService,
            QueryHistoryRepository queryHistoryRepository) {
        this.promptBuilderService = promptBuilderService;
        this.openAIClient = openAIClient;
        this.sqlValidationService = sqlValidationService;
        this.sqlExecutionService = sqlExecutionService;
        this.queryHistoryRepository = queryHistoryRepository;
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
            Result result = sqlExecutionService.execute(cleanedSql);

            // Success — persist and return
            QueryHistory history = new QueryHistory(naturalLanguage, cleanedSql, "table");
            history.setResultRowCount(result.rows().size());
            history.setExecutionTimeMs((int) result.executionTimeMs());
            queryHistoryRepository.save(history);

            log.info("Query succeeded: {} rows in {} ms (attempt {})",
                    result.rows().size(), result.executionTimeMs(), attempt + 1);

            return new QueryResponse(
                    history.getId(),
                    naturalLanguage,
                    cleanedSql,
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
