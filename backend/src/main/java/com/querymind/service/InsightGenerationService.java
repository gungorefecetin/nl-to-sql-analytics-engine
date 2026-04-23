package com.querymind.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.querymind.model.entity.QueryHistory;

@Service
public class InsightGenerationService {

    private static final Logger log = LoggerFactory.getLogger(InsightGenerationService.class);

    private static final String SYSTEM_PROMPT =
            "You are a business intelligence analyst. "
            + "Summarize SQL query results in 2-3 sentences for a non-technical business audience. "
            + "Be specific — mention actual numbers from the data. Keep it actionable.";

    private final OpenAIClient openAIClient;

    public InsightGenerationService(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    /**
     * Generates a 2-3 sentence business insight for a completed query.
     *
     * @param history must have resultData populated (non-null)
     * @return insight text from GPT-4o
     * @throws IllegalArgumentException if resultData is null
     */
    public String generateInsight(QueryHistory history) {
        if (history.getResultData() == null) {
            throw new IllegalArgumentException("No result data available for this query");
        }

        String userPrompt = buildUserPrompt(history);
        log.debug("Generating insight for history id={}", history.getId());

        String insight = openAIClient.chatCompletion(SYSTEM_PROMPT, userPrompt);
        log.debug("Insight generated for history id={}: {} chars", history.getId(), insight.length());
        return insight;
    }

    private String buildUserPrompt(QueryHistory history) {
        return "QUESTION ASKED: " + history.getNaturalLanguage() + "\n"
                + "SQL QUERY: " + history.getGeneratedSql() + "\n"
                + "RESULT SUMMARY: " + history.getResultData()
                + " (Total rows: " + history.getResultRowCount() + ")\n\n"
                + "Write a brief insight.";
    }
}
