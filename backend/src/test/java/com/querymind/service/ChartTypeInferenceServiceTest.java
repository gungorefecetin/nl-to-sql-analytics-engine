package com.querymind.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.querymind.service.ChartTypeInferenceService.ChartType;
import com.querymind.service.SqlExecutionService.ColumnMeta;
import com.querymind.service.SqlExecutionService.QueryResult;

class ChartTypeInferenceServiceTest {

    private final ChartTypeInferenceService service = new ChartTypeInferenceService();

    // ── Empty / edge cases ──────────────────────────────────────────

    @Test
    void emptyRows_returnsTable() {
        QueryResult result = new QueryResult(
                List.of(new ColumnMeta("count", Types.BIGINT)),
                Collections.emptyList());
        assertThat(service.infer(result)).isEqualTo(ChartType.TABLE);
    }

    @Test
    void emptyColumns_returnsTable() {
        QueryResult result = new QueryResult(
                Collections.emptyList(),
                List.of(Map.of("x", 1)));
        assertThat(service.infer(result)).isEqualTo(ChartType.TABLE);
    }

    // ── KPI ─────────────────────────────────────────────────────────

    @Test
    void singleRowSingleNumeric_returnsKpi() {
        // SELECT COUNT(*) AS "Total Orders" → 1 row, 1 numeric column
        QueryResult result = new QueryResult(
                List.of(new ColumnMeta("Total Orders", Types.BIGINT)),
                List.of(Map.of("Total Orders", 99441)));
        assertThat(service.infer(result)).isEqualTo(ChartType.KPI);
    }

    @Test
    void singleRowMultipleNumeric_returnsKpi() {
        // SELECT AVG(price), AVG(freight) → 1 row, 2 numeric columns
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("Avg Price", Types.NUMERIC),
                        new ColumnMeta("Avg Freight", Types.NUMERIC)),
                List.of(Map.of("Avg Price", 120.65, "Avg Freight", 19.99)));
        assertThat(service.infer(result)).isEqualTo(ChartType.KPI);
    }

    @Test
    void singleRowThreeColumns_returnsKpi() {
        // SELECT MIN(price), MAX(price), AVG(price) → 1 row, 3 numeric columns
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("Min", Types.NUMERIC),
                        new ColumnMeta("Max", Types.NUMERIC),
                        new ColumnMeta("Avg", Types.NUMERIC)),
                List.of(Map.of("Min", 0.85, "Max", 6735.0, "Avg", 120.65)));
        assertThat(service.infer(result)).isEqualTo(ChartType.KPI);
    }

    @Test
    void singleRowFourColumns_returnsTable() {
        // 1 row but 4+ columns → not a clean KPI, fall to TABLE
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("A", Types.NUMERIC),
                        new ColumnMeta("B", Types.NUMERIC),
                        new ColumnMeta("C", Types.NUMERIC),
                        new ColumnMeta("D", Types.NUMERIC)),
                List.of(Map.of("A", 1, "B", 2, "C", 3, "D", 4)));
        assertThat(service.infer(result)).isEqualTo(ChartType.TABLE);
    }

    @Test
    void singleRowStringOnly_returnsTable() {
        // 1 row, 1 string column, no numeric → not KPI
        QueryResult result = new QueryResult(
                List.of(new ColumnMeta("Status", Types.VARCHAR)),
                List.of(Map.of("Status", "delivered")));
        assertThat(service.infer(result)).isEqualTo(ChartType.TABLE);
    }

    // ── LINE ────────────────────────────────────────────────────────

    @Test
    void timestampColumnPlusNumeric_returnsLine() {
        // SELECT order_purchase_timestamp, COUNT(*) → temporal type + numeric
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("purchase_date", Types.TIMESTAMP),
                        new ColumnMeta("order_count", Types.BIGINT)),
                List.of(
                        Map.of("purchase_date", "2017-01-01", "order_count", 150),
                        Map.of("purchase_date", "2017-02-01", "order_count", 200)));
        assertThat(service.infer(result)).isEqualTo(ChartType.LINE);
    }

    @Test
    void dateColumnPlusNumeric_returnsLine() {
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("order_date", Types.DATE),
                        new ColumnMeta("revenue", Types.NUMERIC)),
                List.of(
                        Map.of("order_date", "2017-01-01", "revenue", 5000),
                        Map.of("order_date", "2017-02-01", "revenue", 7000)));
        assertThat(service.infer(result)).isEqualTo(ChartType.LINE);
    }

    @Test
    void varcharColumnNamedMonth_returnsLine() {
        // "month" is VARCHAR but column name matches temporal pattern
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("month", Types.VARCHAR),
                        new ColumnMeta("Total Revenue", Types.NUMERIC)),
                List.of(
                        Map.of("month", "2017-01", "Total Revenue", 50000),
                        Map.of("month", "2017-02", "Total Revenue", 70000)));
        assertThat(service.infer(result)).isEqualTo(ChartType.LINE);
    }

    @Test
    void varcharColumnNamedYear_returnsLine() {
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("Year", Types.VARCHAR),
                        new ColumnMeta("Orders", Types.BIGINT)),
                List.of(
                        Map.of("Year", "2016", "Orders", 300),
                        Map.of("Year", "2017", "Orders", 45000),
                        Map.of("Year", "2018", "Orders", 54000)));
        assertThat(service.infer(result)).isEqualTo(ChartType.LINE);
    }

    // ── BAR ─────────────────────────────────────────────────────────

    @Test
    void stringPlusNumeric_lte20Rows_returnsBar() {
        // Top 5 categories by revenue → 5 rows, string + numeric
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("Category", Types.VARCHAR),
                        new ColumnMeta("Total Revenue", Types.NUMERIC)),
                List.of(
                        Map.of("Category", "bed_bath_table", "Total Revenue", 1711682),
                        Map.of("Category", "health_beauty", "Total Revenue", 870000),
                        Map.of("Category", "sports_leisure", "Total Revenue", 650000),
                        Map.of("Category", "furniture_decor", "Total Revenue", 580000),
                        Map.of("Category", "computers", "Total Revenue", 520000)));
        assertThat(service.infer(result)).isEqualTo(ChartType.BAR);
    }

    @Test
    void stringPlusNumeric_moreThan20Rows_returnsTable() {
        // 25 rows of category + revenue → too many for a bar chart
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("Category", Types.VARCHAR),
                new ColumnMeta("Count", Types.BIGINT));
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(Map.of("Category", "cat_" + i, "Count", i * 100));
        }
        QueryResult result = new QueryResult(columns, rows);
        assertThat(service.infer(result)).isEqualTo(ChartType.TABLE);
    }

    @Test
    void stringPlusNumeric_exactly20Rows_returnsBar() {
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("State", Types.VARCHAR),
                new ColumnMeta("Orders", Types.BIGINT));
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(Map.of("State", "ST" + i, "Orders", i * 50));
        }
        QueryResult result = new QueryResult(columns, rows);
        assertThat(service.infer(result)).isEqualTo(ChartType.BAR);
    }

    // ── TABLE (fallback) ────────────────────────────────────────────

    @Test
    void manyColumns_returnsTable() {
        // Wide result: 5 columns, mix of types → TABLE
        QueryResult result = new QueryResult(
                List.of(
                        new ColumnMeta("order_id", Types.VARCHAR),
                        new ColumnMeta("customer_id", Types.VARCHAR),
                        new ColumnMeta("status", Types.VARCHAR),
                        new ColumnMeta("price", Types.NUMERIC),
                        new ColumnMeta("freight", Types.NUMERIC)),
                List.of(
                        Map.of("order_id", "abc", "customer_id", "def",
                                "status", "delivered", "price", 100, "freight", 15)));
        assertThat(service.infer(result)).isEqualTo(ChartType.TABLE);
    }

    @Test
    void allNullNumericColumn_returnsKpi() {
        // 1 row, 1 numeric column with null value — still KPI (metadata says numeric)
        QueryResult result = new QueryResult(
                List.of(new ColumnMeta("Avg Score", Types.NUMERIC)),
                List.of(Collections.singletonMap("Avg Score", null)));
        assertThat(service.infer(result)).isEqualTo(ChartType.KPI);
    }
}
