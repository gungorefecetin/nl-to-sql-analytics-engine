package com.querymind.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.querymind.model.dto.InsightRequest;
import com.querymind.model.dto.InsightResponse;
import com.querymind.model.dto.ResponseEnvelope;
import com.querymind.model.entity.QueryHistory;
import com.querymind.repository.QueryHistoryRepository;
import com.querymind.service.InsightGenerationService;
import com.querymind.service.RateLimiterService;
import com.querymind.util.ClientIpExtractor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/insight")
@Validated
@Tag(name = "Insight", description = "AI-generated summaries of previous query results")
public class InsightController {

    private final QueryHistoryRepository queryHistoryRepository;
    private final InsightGenerationService insightGenerationService;
    private final RateLimiterService rateLimiterService;

    public InsightController(QueryHistoryRepository queryHistoryRepository,
                             InsightGenerationService insightGenerationService,
                             RateLimiterService rateLimiterService) {
        this.queryHistoryRepository = queryHistoryRepository;
        this.insightGenerationService = insightGenerationService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping
    @Operation(summary = "Generate an insight for a previous query",
            description = "Uses GPT-4o to produce a 2-3 sentence business-analyst summary of "
                    + "the stored results for the given query history ID. Rate-limited to 20 "
                    + "requests per minute per IP.")
    public ResponseEntity<ResponseEnvelope<InsightResponse>> generateInsight(
            @Valid @RequestBody InsightRequest request,
            HttpServletRequest httpRequest) {
        rateLimiterService.check(ClientIpExtractor.extract(httpRequest));
        QueryHistory history = queryHistoryRepository.findById(request.historyId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Query history not found: " + request.historyId()));

        if (history.getResultData() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No result data available for this query");
        }

        String insight = insightGenerationService.generateInsight(history);
        return ResponseEntity.ok(ResponseEnvelope.success(new InsightResponse(insight)));
    }
}
