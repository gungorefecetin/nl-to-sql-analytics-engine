package com.querymind.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * Read-only datasource for executing user-generated SQL.
     * Uses a PostgreSQL role with SELECT-only permissions.
     * Even if SQL validation is somehow bypassed, writes are impossible.
     */
    @Bean
    @ConfigurationProperties(prefix = "readonly.datasource")
    public DataSource readonlyDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * JdbcTemplate wired to the read-only datasource.
     * Used by SqlExecutionService (Day 2) for running AI-generated queries.
     */
    @Bean
    public JdbcTemplate readonlyJdbcTemplate(@Qualifier("readonlyDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(30);
        return jdbcTemplate;
    }
}
