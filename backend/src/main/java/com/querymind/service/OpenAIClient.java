package com.querymind.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querymind.config.OpenAIConfig;
import com.querymind.exception.OpenAIClientException;

@Service
public class OpenAIClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);

    private final RestClient restClient;
    private final OpenAIConfig config;
    private final ObjectMapper objectMapper;

    public OpenAIClient(RestClient openAIRestClient, OpenAIConfig config, ObjectMapper objectMapper) {
        this.restClient = openAIRestClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a chat completion request to OpenAI and returns the assistant's reply as a plain string.
     *
     * @param systemPrompt the system-level instruction (e.g., "You are a PostgreSQL expert...")
     * @param userPrompt   the user-level message (schema context + natural language question)
     * @return the model's response text (expected to be a SQL query)
     * @throws OpenAIClientException on any failure — network, rate limit, malformed response
     */
    public String chatCompletion(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "max_tokens", config.getMaxTokens(),
                "temperature", config.getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new OpenAIClientException("OpenAI API call failed: " + e.getMessage(), e);
        }

        return extractContent(responseJson);
    }

    private String extractContent(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty() || choices.isMissingNode()) {
                throw new OpenAIClientException("OpenAI returned no choices in response");
            }
            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new OpenAIClientException("OpenAI returned empty content in response");
            }
            log.debug("OpenAI response length: {} chars", content.length());
            return content.strip();
        } catch (OpenAIClientException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAIClientException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }
}
