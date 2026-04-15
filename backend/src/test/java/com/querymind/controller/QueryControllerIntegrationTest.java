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
    void submitQuery_validQuestion_returnsResultAndPersistsHistory() throws Exception {
        // Mock OpenAI to return a valid SELECT
        String fakeSql = "SELECT COUNT(*) AS \"Total Orders\" FROM olist_orders_dataset";
        when(openAIClient.chatCompletion(anyString(), anyString())).thenReturn(fakeSql);

        long countBefore = queryHistoryRepository.count();

        MvcResult result = mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguage\": \"How many orders are there?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedSql").value(fakeSql))
                .andExpect(jsonPath("$.naturalLanguage").value("How many orders are there?"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]['Total Orders']").isNumber())
                .andExpect(jsonPath("$.executionTimeMs").isNumber())
                .andExpect(jsonPath("$.historyId").isNumber())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();

        // Verify history was persisted
        long countAfter = queryHistoryRepository.count();
        assertThat(countAfter).isEqualTo(countBefore + 1);

        QueryHistory latest = queryHistoryRepository.findTop20ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getNaturalLanguage()).isEqualTo("How many orders are there?");
        assertThat(latest.getGeneratedSql()).isEqualTo(fakeSql);
        assertThat(latest.getResultRowCount()).isEqualTo(1);
        assertThat(latest.getErrorMessage()).isNull();
    }
}
