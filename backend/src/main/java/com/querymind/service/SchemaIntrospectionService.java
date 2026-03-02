package com.querymind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querymind.model.dto.SchemaResponse;
import com.querymind.model.entity.SchemaCache;
import com.querymind.repository.SchemaCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchemaIntrospectionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIntrospectionService.class);

    private static final List<String> TARGET_TABLES = List.of(
            "olist_customers_dataset",
            "olist_orders_dataset",
            "olist_products_dataset",
            "olist_sellers_dataset",
            "olist_order_items_dataset",
            "olist_order_payments_dataset",
            "olist_order_reviews_dataset",
            "olist_geolocation_dataset",
            "product_category_name_translation"
    );

    // Columns likely to have enum-like values worth sampling
    private static final Set<String> ENUM_LIKE_COLUMNS = Set.of(
            "order_status", "payment_type", "customer_state", "seller_state",
            "geolocation_state", "product_category_name"
    );

    private static final int SAMPLE_VALUE_LIMIT = 10;

    private final JdbcTemplate jdbcTemplate;
    private final SchemaCacheRepository schemaCacheRepository;
    private final ObjectMapper objectMapper;

    public SchemaIntrospectionService(JdbcTemplate jdbcTemplate,
                                       SchemaCacheRepository schemaCacheRepository,
                                       ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaCacheRepository = schemaCacheRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void introspectOnStartup() {
        log.info("Starting schema introspection...");
        try {
            for (String tableName : TARGET_TABLES) {
                introspectTable(tableName);
            }
            log.info("Schema introspection complete for {} tables.", TARGET_TABLES.size());
        } catch (Exception e) {
            log.error("Schema introspection failed: {}", e.getMessage(), e);
        }
    }

    private void introspectTable(String tableName) {
        try {
            List<SchemaResponse.ColumnInfo> columns = fetchColumns(tableName);
            long rowCount = fetchRowCount(tableName);

            SchemaResponse.TableInfo tableInfo = new SchemaResponse.TableInfo(tableName, columns, rowCount);
            String json = objectMapper.writeValueAsString(tableInfo);

            SchemaCache cache = schemaCacheRepository.findByTableName(tableName)
                    .orElse(new SchemaCache());
            cache.setTableName(tableName);
            cache.setSchemaJson(json);
            schemaCacheRepository.save(cache);

            log.info("  Cached schema for {} ({} columns, {} rows)", tableName, columns.size(), rowCount);
        } catch (Exception e) {
            log.warn("  Failed to introspect table {}: {}", tableName, e.getMessage());
        }
    }

    private List<SchemaResponse.ColumnInfo> fetchColumns(String tableName) {
        String sql = """
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String columnName = rs.getString("column_name");
            String dataType = rs.getString("data_type");
            List<String> sampleValues = List.of();

            if (ENUM_LIKE_COLUMNS.contains(columnName)) {
                sampleValues = fetchDistinctValues(tableName, columnName);
            }

            return new SchemaResponse.ColumnInfo(columnName, dataType, sampleValues);
        }, tableName);
    }

    private List<String> fetchDistinctValues(String tableName, String columnName) {
        try {
            // Safe: tableName and columnName come from information_schema, not user input
            String sql = String.format(
                    "SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL ORDER BY %s LIMIT %d",
                    columnName, tableName, columnName, columnName, SAMPLE_VALUE_LIMIT
            );
            return jdbcTemplate.queryForList(sql, String.class);
        } catch (Exception e) {
            log.warn("Could not fetch distinct values for {}.{}: {}", tableName, columnName, e.getMessage());
            return List.of();
        }
    }

    private long fetchRowCount(String tableName) {
        try {
            // Safe: tableName comes from our hardcoded TARGET_TABLES list
            String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Could not fetch row count for {}: {}", tableName, e.getMessage());
            return 0;
        }
    }

    /**
     * Returns all cached schema information as a SchemaResponse.
     * Used by SchemaController and PromptBuilderService (Day 2).
     */
    public SchemaResponse getSchema() {
        List<SchemaCache> caches = schemaCacheRepository.findAll();
        List<SchemaResponse.TableInfo> tables = new ArrayList<>();

        for (SchemaCache cache : caches) {
            try {
                SchemaResponse.TableInfo tableInfo = objectMapper.readValue(
                        cache.getSchemaJson(), SchemaResponse.TableInfo.class
                );
                tables.add(tableInfo);
            } catch (Exception e) {
                log.warn("Failed to parse schema cache for {}: {}", cache.getTableName(), e.getMessage());
            }
        }

        return new SchemaResponse(tables);
    }
}
