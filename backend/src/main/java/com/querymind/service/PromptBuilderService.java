package com.querymind.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querymind.model.entity.SchemaCache;
import com.querymind.repository.SchemaCacheRepository;

@Service
public class PromptBuilderService {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilderService.class);

    private static final int MAX_SAMPLE_VALUES = 10;

    // Patterns for values that aren't useful as sample data in prompts
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
    private static final Pattern ISO_TIMESTAMP_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T");

    // Exact text from PRD section 8
    static final String SYSTEM_PROMPT = """
            You are a PostgreSQL expert. Your job is to convert natural language questions \
            into valid, executable PostgreSQL SELECT queries.

            Rules:
            1. Only generate SELECT statements. Never use INSERT, UPDATE, DELETE, DROP, or any DDL.
            2. Always use table aliases for readability.
            3. Prefer readable column aliases (e.g., AS "Total Revenue" instead of AS total_revenue).
            4. Limit results to 500 rows maximum unless the user explicitly asks for more.
            5. Use ILIKE for string matching (case-insensitive).
            6. For date operations, use PostgreSQL date functions (DATE_TRUNC, EXTRACT, etc.).
            7. Return ONLY the SQL query — no explanation, no markdown, no code fences.
            8. If the question cannot be answered with the available schema, return: ERROR: <reason>""";

    private final SchemaCacheRepository schemaCacheRepository;
    private final ObjectMapper objectMapper;

    public PromptBuilderService(SchemaCacheRepository schemaCacheRepository, ObjectMapper objectMapper) {
        this.schemaCacheRepository = schemaCacheRepository;
        this.objectMapper = objectMapper;
    }

    public PromptPair buildPrompt(String naturalLanguageQuestion) {
        List<SchemaCache> allSchemas = schemaCacheRepository.findAll();

        StringBuilder schemaContext = new StringBuilder();
        StringBuilder sampleData = new StringBuilder();

        for (SchemaCache cache : allSchemas) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = objectMapper.readValue(cache.getSchemaJson(), Map.class);
                appendTableSchema(schemaContext, schema);
                appendSampleValues(sampleData, schema);
            } catch (JsonProcessingException e) {
                log.warn("Skipping malformed schema cache for {}: {}", cache.getTableName(), e.getMessage());
            }
        }

        String userPrompt = """
                DATABASE SCHEMA:
                %s
                SAMPLE DATA (first 3 rows per relevant table):
                %s
                QUESTION:
                %s

                Generate a PostgreSQL SELECT query to answer this question."""
                .formatted(schemaContext, sampleData, naturalLanguageQuestion);

        log.debug("Built prompt: system={} chars, user={} chars", SYSTEM_PROMPT.length(), userPrompt.length());
        return new PromptPair(SYSTEM_PROMPT, userPrompt);
    }

    @SuppressWarnings("unchecked")
    private void appendTableSchema(StringBuilder sb, Map<String, Object> schema) {
        String tableName = (String) schema.get("table_name");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) schema.get("columns");
        List<Map<String, Object>> sampleRows = (List<Map<String, Object>>) schema.get("sample_rows");

        sb.append("TABLE: ").append(tableName).append('\n');
        sb.append("COLUMNS:\n");

        for (Map<String, Object> col : columns) {
            String colName = (String) col.get("column_name");
            String dataType = (String) col.get("data_type");
            sb.append("  - ").append(colName).append(" (").append(dataType).append(')');

            // Extract distinct sample values for enum-like columns
            Set<String> distinctValues = extractDistinctValues(colName, sampleRows);
            if (!distinctValues.isEmpty() && distinctValues.size() <= MAX_SAMPLE_VALUES) {
                sb.append(": sample values = ").append(distinctValues);
            }

            sb.append('\n');
        }
        sb.append('\n');
    }

    @SuppressWarnings("unchecked")
    private void appendSampleValues(StringBuilder sb, Map<String, Object> schema) {
        String tableName = (String) schema.get("table_name");
        List<Map<String, Object>> sampleRows = (List<Map<String, Object>>) schema.get("sample_rows");

        if (sampleRows == null || sampleRows.isEmpty()) {
            return;
        }

        sb.append(tableName).append(":\n");
        for (Map<String, Object> row : sampleRows) {
            sb.append("  ").append(row).append('\n');
        }
        sb.append('\n');
    }

    private Set<String> extractDistinctValues(String columnName, List<Map<String, Object>> sampleRows) {
        if (sampleRows == null || sampleRows.isEmpty()) {
            return Set.of();
        }

        Set<String> values = new HashSet<>();
        for (Map<String, Object> row : sampleRows) {
            Object val = row.get(columnName);
            if (val != null) {
                String str = val.toString();
                if (isUsefulSampleValue(str)) {
                    values.add(str);
                }
            }
        }

        // If all sample values are distinct and there are 3+, this is likely a high-cardinality column
        // (e.g., zip codes, cities) — not useful as enum-like sample values
        if (values.size() >= 3) {
            return Set.of();
        }

        return values;
    }

    private boolean isUsefulSampleValue(String value) {
        if (value.length() > 30) return false;
        if (UUID_PATTERN.matcher(value).matches()) return false;
        if (ISO_TIMESTAMP_PATTERN.matcher(value).matches()) return false;
        return true;
    }

    public record PromptPair(String systemPrompt, String userPrompt) {}
}
