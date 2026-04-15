package com.querymind.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.querymind.model.dto.QueryRequest;
import com.querymind.model.dto.QueryResponse;
import com.querymind.service.QueryOrchestrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/query")
@Validated
public class QueryController {

    private final QueryOrchestrationService orchestrationService;

    public QueryController(QueryOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping
    public ResponseEntity<QueryResponse> submitQuery(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = orchestrationService.executeQuery(request.naturalLanguage());
        return ResponseEntity.ok(response);
    }
}
