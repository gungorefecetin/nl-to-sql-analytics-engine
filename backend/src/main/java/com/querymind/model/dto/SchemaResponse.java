package com.querymind.model.dto;

import java.util.List;

public record SchemaResponse(List<TableInfo> tables) {

    public record TableInfo(
            String tableName,
            List<ColumnInfo> columns,
            long rowCount
    ) {}

    public record ColumnInfo(
            String name,
            String type,
            List<String> sampleValues
    ) {}
}
