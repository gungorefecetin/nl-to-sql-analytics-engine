package com.querymind.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourceConfig {

    // ── Primary (admin) datasource — used by JPA for app tables ──

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return primaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }

    // ── Readonly datasource — used for AI-generated SQL execution ──

    @Bean
    @ConfigurationProperties("readonly.datasource")
    public DataSourceProperties readonlyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Qualifier("readonlyDataSource")
    public DataSource readonlyDataSource() {
        return readonlyDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }

    @Bean
    @Qualifier("readonlyJdbcTemplate")
    public JdbcTemplate readonlyJdbcTemplate(@Qualifier("readonlyDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
