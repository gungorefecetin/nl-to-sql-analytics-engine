package com.querymind.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.querymind.model.entity.QueryHistory;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    List<QueryHistory> findTop20ByOrderByCreatedAtDesc();
}
