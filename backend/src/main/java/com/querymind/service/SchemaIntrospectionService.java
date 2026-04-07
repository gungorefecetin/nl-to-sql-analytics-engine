package com.querymind.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querymind.model.entity.SchemaCache;
import com.querymind.repository.SchemaCacheRepository;

@Service
public class SchemaIntrospectionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIntrospectionService.class);
    private static final int SAMPLE_ROW_LIMIT = 3;

    private final JdbcTemplate readonlyJdbcTemplate;
    private final SchemaCacheRepository schemaCacheRepository;
    private final ObjectMapper objectMapper;

    public SchemaIntrospectionService(
            @Qualifier("readonlyJdbcTemplate") JdbcTemplate readonlyJdbcTemplate,
            SchemaCacheRepository schemaCacheRepository,
            ObjectMapper objectMapper) {
        this.readonlyJdbcTemplate = readonlyJdbcTemplate;
        this.schemaCacheRepository = schemaCacheRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void introspectOnStartup() {
        log.info("Starting schema introspection...");
        List<String> tableNames = discoverTableNames();
        log.info("Discovered {} tables: {}", tableNames.size(), tableNames);

        for (String tableName : tableNames) {
            try {
                Map<String, Object> schemaInfo = buildSchemaInfo(tableName);
                String json = objectMapper.writeValueAsString(schemaInfo);
                upsert(tableName, json);
                log.info("  Cached schema for {}", tableName);
            } catch (JsonProcessingException e) {
                log.error("  Failed to serialize schema for {}: {}", tableName, e.getMessage());
            }
        }
        log.info("Schema introspection complete.");
    }

    private List<String> discoverTableNames() {
        return readonlyJdbcTemplate.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND (table_name LIKE 'olist_%' OR table_name LIKE 'product_%')
                ORDER BY table_name
                """,
                String.class);
    }

    private Map<String, Object> buildSchemaInfo(String tableName) {
        List<Map<String, Object>> columns = readonlyJdbcTemplate.queryForList(
                """
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """,
                tableName);

        List<Map<String, Object>> sampleRows = readonlyJdbcTemplate.queryForList(
                "SELECT * FROM " + quoteIdentifier(tableName) + " LIMIT " + SAMPLE_ROW_LIMIT);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table_name", tableName);
        result.put("columns", columns);
        result.put("sample_rows", sampleRows);
        result.put("row_count", readonlyJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + quoteIdentifier(tableName), Long.class));
        return result;
    }

    private void upsert(String tableName, String json) {
        SchemaCache existing = schemaCacheRepository.findByTableName(tableName).orElse(null);
        if (existing != null) {
            existing.setSchemaJson(json);
            existing.setUpdatedAt(LocalDateTime.now());
            schemaCacheRepository.save(existing);
        } else {
            schemaCacheRepository.save(new SchemaCache(tableName, json));
        }
    }

    public List<Map<String, Object>> getAllSchemas() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SchemaCache cache : schemaCacheRepository.findAll()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = objectMapper.readValue(cache.getSchemaJson(), Map.class);
                result.add(schema);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse cached schema for {}: {}", cache.getTableName(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Double-quote a SQL identifier to prevent injection.
     * Table names come from information_schema (trusted), but we quote defensively.
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
