package com.querymind.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test that prints the actual generated prompt for manual inspection.
 * Requires the database with schema_cache populated (run via Docker).
 */
@SpringBootTest
class PromptBuilderServiceTest {

    @Autowired
    private PromptBuilderService promptBuilderService;

    @Test
    void buildPrompt_containsAllTablesAndQuestion() {
        PromptBuilderService.PromptPair pair = promptBuilderService.buildPrompt(
                "What are the top 5 product categories by revenue?");

        // System prompt is the PRD-defined constant
        assertThat(pair.systemPrompt()).contains("PostgreSQL expert");
        assertThat(pair.systemPrompt()).contains("Return ONLY the SQL query");

        // User prompt structure
        assertThat(pair.userPrompt()).contains("DATABASE SCHEMA:");
        assertThat(pair.userPrompt()).contains("SAMPLE DATA");
        assertThat(pair.userPrompt()).contains("QUESTION:");
        assertThat(pair.userPrompt()).contains("top 5 product categories by revenue");

        // All 9 Olist tables present
        assertThat(pair.userPrompt()).contains("TABLE: olist_orders_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: olist_customers_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: olist_order_items_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: olist_order_payments_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: olist_order_reviews_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: olist_products_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: olist_sellers_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: olist_geolocation_dataset");
        assertThat(pair.userPrompt()).contains("TABLE: product_category_name_translation");

        // Print for manual inspection
        System.out.println("=== SYSTEM PROMPT (" + pair.systemPrompt().length() + " chars) ===");
        System.out.println(pair.systemPrompt());
        System.out.println();
        System.out.println("=== USER PROMPT (" + pair.userPrompt().length() + " chars) ===");
        System.out.println(pair.userPrompt());
    }
}
