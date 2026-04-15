package com.querymind.service;

import java.util.ArrayList;
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

    public record Message(String role, String content) {}

    /**
     * Convenience method for single-turn conversations.
     * Delegates to the multi-message overload.
     */
    public String chatCompletion(String systemPrompt, String userPrompt) {
        return chatCompletion(systemPrompt, List.of(new Message("user", userPrompt)));
    }

    /**
     * Sends a chat completion request with full conversation history.
     * Used by the retry loop to include the assistant's prior bad SQL + error correction.
     *
     * @param systemPrompt the system-level instruction
     * @param messages     ordered conversation: user → assistant → user → ...
     * @return the model's response text
     * @throws OpenAIClientException on any failure
     */
    public String chatCompletion(String systemPrompt, List<Message> messages) {
        List<Map<String, String>> apiMessages = new ArrayList<>();
        apiMessages.add(Map.of("role", "system", "content", systemPrompt));
        for (Message msg : messages) {
            apiMessages.add(Map.of("role", msg.role(), "content", msg.content()));
        }

        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "max_tokens", config.getMaxTokens(),
                "temperature", config.getTemperature(),
                "messages", apiMessages
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
