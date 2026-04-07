package com.querymind.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.querymind.service.SchemaIntrospectionService;

@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final SchemaIntrospectionService schemaIntrospectionService;

    public SchemaController(SchemaIntrospectionService schemaIntrospectionService) {
        this.schemaIntrospectionService = schemaIntrospectionService;
    }

    @GetMapping
    public List<Map<String, Object>> getSchema() {
        return schemaIntrospectionService.getAllSchemas();
    }
}
