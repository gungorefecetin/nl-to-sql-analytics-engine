package com.querymind.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.querymind.model.entity.SchemaCache;

public interface SchemaCacheRepository extends JpaRepository<SchemaCache, Long> {

    Optional<SchemaCache> findByTableName(String tableName);
}
