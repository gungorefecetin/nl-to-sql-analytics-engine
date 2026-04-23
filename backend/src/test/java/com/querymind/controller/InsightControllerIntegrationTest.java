package com.querymind.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querymind.model.entity.QueryHistory;
import com.querymind.repository.QueryHistoryRepository;
import com.querymind.service.OpenAIClient;

@SpringBootTest
@AutoConfigureMockMvc
class InsightControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryHistoryRepository queryHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OpenAIClient openAIClient;

    @Test
    void queryThenInsight_fullFlow() throws Exception {
        String fakeSql = "SELECT COUNT(*) AS \"Total Orders\" FROM olist_orders_dataset";
        String fakeInsight = "The dataset contains 99,441 orders. This represents a substantial "
                + "transaction volume that could support detailed trend analysis and segmentation.";

        when(openAIClient.chatCompletion(anyString(), anyString()))
                .thenReturn(fakeSql)
                .thenReturn(fakeInsight);

        // Step 1: Submit a query to get a historyId
        MvcResult queryResult = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguage\": \"How many orders are there?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.historyId").isNumber())
                .andReturn();

        JsonNode queryJson = objectMapper.readTree(queryResult.getResponse().getContentAsString());
        long historyId = queryJson.get("data").get("historyId").asLong();

        // Verify result_data was persisted
        QueryHistory history = queryHistoryRepository.findById(historyId).orElseThrow();
        assertThat(history.getResultData()).isNotNull();
        assertThat(history.getResultData()).contains("Total Orders");

        // Step 2: Call insight endpoint
        mockMvc.perform(post("/api/insight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyId\": " + historyId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insight").value(fakeInsight));
    }

    @Test
    void insight_notFoundHistoryId_returnsErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/insight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historyId\": 999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    void insight_nullHistoryId_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/insight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
