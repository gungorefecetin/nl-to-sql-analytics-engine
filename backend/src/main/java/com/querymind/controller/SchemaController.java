package com.querymind.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.querymind.model.dto.ResponseEnvelope;
import com.querymind.service.SchemaIntrospectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/schema")
@Tag(name = "Schema", description = "Database schema introspection")
public class SchemaController {

    private final SchemaIntrospectionService schemaIntrospectionService;

    public SchemaController(SchemaIntrospectionService schemaIntrospectionService) {
        this.schemaIntrospectionService = schemaIntrospectionService;
    }

    @GetMapping
    @Operation(summary = "Get all tables and columns",
            description = "Returns cached schema metadata (tables and columns) used for NL-to-SQL prompt construction.")
    public ResponseEntity<ResponseEnvelope<List<Map<String, Object>>>> getSchema() {
        return ResponseEntity.ok(ResponseEnvelope.success(schemaIntrospectionService.getAllSchemas()));
    }
}
