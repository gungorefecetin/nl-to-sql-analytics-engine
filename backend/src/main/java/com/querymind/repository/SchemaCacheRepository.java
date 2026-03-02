package com.querymind.repository;

import com.querymind.model.entity.SchemaCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchemaCacheRepository extends JpaRepository<SchemaCache, Long> {

    Optional<SchemaCache> findByTableName(String tableName);
}
