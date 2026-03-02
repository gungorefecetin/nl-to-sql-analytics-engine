package com.querymind.controller;

import com.querymind.model.dto.SchemaResponse;
import com.querymind.service.SchemaIntrospectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final SchemaIntrospectionService schemaIntrospectionService;

    public SchemaController(SchemaIntrospectionService schemaIntrospectionService) {
        this.schemaIntrospectionService = schemaIntrospectionService;
    }

    @GetMapping
    public ResponseEntity<SchemaResponse> getSchema() {
        return ResponseEntity.ok(schemaIntrospectionService.getSchema());
    }
}
