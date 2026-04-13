package com.querymind.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAIClientIntegrationTest {

    @Autowired
    private OpenAIClient openAIClient;

    @Test
    void chatCompletion_returnsSQL() {
        String response = openAIClient.chatCompletion(
                "You are a SQL expert. Return only SQL, no explanation.",
                "Write a SELECT 1 query.");

        assertThat(response).isNotNull();
        assertThat(response.toUpperCase()).contains("SELECT");
    }
}
