package com.querymind.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.querymind.model.dto.QueryHistoryResponse;
import com.querymind.model.dto.QueryRequest;
import com.querymind.model.dto.QueryResponse;
import com.querymind.model.dto.ResponseEnvelope;
import com.querymind.repository.QueryHistoryRepository;
import com.querymind.service.QueryOrchestrationService;
import com.querymind.service.RateLimiterService;
import com.querymind.util.ClientIpExtractor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/query")
@Validated
@Tag(name = "Query", description = "Natural-language query submission and history")
public class QueryController {

    private final QueryOrchestrationService orchestrationService;
    private final QueryHistoryRepository queryHistoryRepository;
    private final RateLimiterService rateLimiterService;

    public QueryController(QueryOrchestrationService orchestrationService,
                           QueryHistoryRepository queryHistoryRepository,
                           RateLimiterService rateLimiterService) {
        this.orchestrationService = orchestrationService;
        this.queryHistoryRepository = queryHistoryRepository;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping
    @Operation(summary = "Submit a natural-language query",
            description = "Translates a natural-language question into SQL via GPT-4o, "
                    + "executes it against the read-only datasource, and returns the results "
                    + "with an inferred chart type. Rate-limited to 20 requests per minute per IP.")
    public ResponseEntity<ResponseEnvelope<QueryResponse>> submitQuery(
            @Valid @RequestBody QueryRequest request,
            HttpServletRequest httpRequest) {
        rateLimiterService.check(ClientIpExtractor.extract(httpRequest));
        QueryResponse response = orchestrationService.executeQuery(request.naturalLanguage());
        return ResponseEntity.ok(ResponseEnvelope.success(response));
    }

    @GetMapping("/history")
    @Operation(summary = "List previous queries",
            description = "Returns a paginated list of past queries sorted by most recent. "
                    + "Page size is clamped to a maximum of 50.")
    public ResponseEntity<ResponseEnvelope<Page<QueryHistoryResponse>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 50);
        PageRequest pageRequest = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QueryHistoryResponse> historyPage = queryHistoryRepository.findAll(pageRequest)
                .map(h -> new QueryHistoryResponse(
                        h.getId(),
                        h.getNaturalLanguage(),
                        h.getChartType(),
                        h.getResultRowCount(),
                        h.getExecutionTimeMs(),
                        h.getCreatedAt()));
        return ResponseEntity.ok(ResponseEnvelope.success(historyPage));
    }
}
