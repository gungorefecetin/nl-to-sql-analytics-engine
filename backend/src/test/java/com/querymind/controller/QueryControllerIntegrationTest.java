package com.querymind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.querymind.model.entity.QueryHistory;
import com.querymind.repository.QueryHistoryRepository;
import com.querymind.service.OpenAIClient;

@SpringBootTest
@AutoConfigureMockMvc
class QueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryHistoryRepository queryHistoryRepository;

    @MockBean
    private OpenAIClient openAIClient;

    @Test
    void submitQuery_countQuery_returnsKpiWithMetrics() throws Exception {
        String fakeSql = "SELECT COUNT(*) AS \"Total Orders\" FROM olist_orders_dataset";
        when(openAIClient.chatCompletion(anyString(), anyString())).thenReturn(fakeSql);

        long countBefore = queryHistoryRepository.count();

        MvcResult result = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguage\": \"How many orders are there?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generatedSql").value(fakeSql))
                .andExpect(jsonPath("$.data.naturalLanguage").value("How many orders are there?"))
                .andExpect(jsonPath("$.data.rowCount").value(1))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data[0]['Total Orders']").isNumber())
                .andExpect(jsonPath("$.data.executionTimeMs").isNumber())
                .andExpect(jsonPath("$.data.historyId").isNumber())
                // Chart type assertions
                .andExpect(jsonPath("$.data.chartType").value("kpi"))
                .andExpect(jsonPath("$.data.chartData").exists())
                .andExpect(jsonPath("$.data.chartData.metrics").isArray())
                .andExpect(jsonPath("$.data.chartData.metrics[0].label").value("Total Orders"))
                .andExpect(jsonPath("$.data.chartData.metrics[0].value").isNumber())
                .andReturn();

        // Verify KPI chartData does NOT contain xKey/yKey/rows (NON_NULL serialization)
        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("\"xKey\"");
        assertThat(json).doesNotContain("\"yKey\"");
        assertThat(json).contains("\"metrics\"");

        // Verify history was persisted with chart type
        long countAfter = queryHistoryRepository.count();
        assertThat(countAfter).isEqualTo(countBefore + 1);

        QueryHistory latest = queryHistoryRepository.findTop20ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getNaturalLanguage()).isEqualTo("How many orders are there?");
        assertThat(latest.getGeneratedSql()).isEqualTo(fakeSql);
        assertThat(latest.getChartType()).isEqualTo("kpi");
        assertThat(latest.getResultRowCount()).isEqualTo(1);
        assertThat(latest.getErrorMessage()).isNull();
    }

    @Test
    void submitQuery_categoryRevenue_returnsBarChart() throws Exception {
        String fakeSql = "SELECT p.product_category_name AS \"Category\", SUM(oi.price) AS \"Revenue\" "
                + "FROM olist_order_items_dataset oi "
                + "JOIN olist_products_dataset p ON oi.product_id = p.product_id "
                + "GROUP BY p.product_category_name "
                + "ORDER BY SUM(oi.price) DESC LIMIT 5";
        when(openAIClient.chatCompletion(anyString(), anyString())).thenReturn(fakeSql);

        MvcResult result = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguage\": \"Top 5 categories by revenue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chartType").value("bar"))
                .andExpect(jsonPath("$.data.chartData.xKey").value("Category"))
                .andExpect(jsonPath("$.data.chartData.yKey").value("Revenue"))
                .andExpect(jsonPath("$.data.chartData.rows").isArray())
                .andExpect(jsonPath("$.data.rowCount").value(5))
                .andReturn();

        // BAR chartData should NOT contain "metrics" (NON_NULL)
        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("\"metrics\"");
        assertThat(json).contains("\"xKey\"");
        assertThat(json).contains("\"yKey\"");
    }

    @Test
    void submitQuery_monthlyOrders_returnsLineChart() throws Exception {
        String fakeSql = "SELECT TO_CHAR(o.order_purchase_timestamp, 'YYYY-MM') AS \"Month\", "
                + "COUNT(*) AS \"Orders\" "
                + "FROM olist_orders_dataset o "
                + "GROUP BY TO_CHAR(o.order_purchase_timestamp, 'YYYY-MM') "
                + "ORDER BY \"Month\"";
        when(openAIClient.chatCompletion(anyString(), anyString())).thenReturn(fakeSql);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguage\": \"Show monthly order volume\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chartType").value("line"))
                .andExpect(jsonPath("$.data.chartData.xKey").value("Month"))
                .andExpect(jsonPath("$.data.chartData.yKey").value("Orders"))
                .andExpect(jsonPath("$.data.chartData.rows").isArray());
    }

    @Test
    void submitQuery_wideResult_returnsTable() throws Exception {
        String fakeSql = "SELECT o.order_id, o.customer_id, o.order_status, "
                + "o.order_purchase_timestamp, o.order_delivered_customer_date "
                + "FROM olist_orders_dataset o LIMIT 25";
        when(openAIClient.chatCompletion(anyString(), anyString())).thenReturn(fakeSql);

        MvcResult result = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguage\": \"Show me recent orders\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chartType").value("table"))
                .andExpect(jsonPath("$.data.rowCount").value(25))
                .andReturn();

        // TABLE: chartData should be absent (null → not serialized by NON_NULL on QueryResponse)
        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("\"chartData\"");
    }

    @Test
    void getHistory_returnsPaginatedResults() throws Exception {
        // First, create a query so there's at least one history entry
        String fakeSql = "SELECT COUNT(*) AS \"Total\" FROM olist_orders_dataset";
        when(openAIClient.chatCompletion(anyString(), anyString())).thenReturn(fakeSql);
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguage\": \"count orders\"}"))
                .andExpect(status().isOk());

        // Now fetch history
        mockMvc.perform(get("/api/query/history")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").isNumber())
                .andExpect(jsonPath("$.data.content[0].naturalLanguage").isString())
                .andExpect(jsonPath("$.data.content[0].chartType").isString())
                .andExpect(jsonPath("$.data.content[0].createdAt").isString())
                // Slim DTO — should NOT contain generatedSql or data
                .andExpect(jsonPath("$.data.content[0].generatedSql").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].data").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    void getHistory_defaultPagination_returns20() throws Exception {
        mockMvc.perform(get("/api/query/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void getHistory_sizeExceeds50_clampedTo50() throws Exception {
        mockMvc.perform(get("/api/query/history").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }
}
