package com.bowon.cpm.admin;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.service.AiDecisionService;
import com.bowon.cpm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiDecisionService aiDecisionService;

    /**
     * AI decision creation.
     * POST /api/ai/decisions/{stockCode}
     */
    @PostMapping("/decisions/{stockCode}")
    public ApiResponse<Map<String, Object>> generateDecision(@PathVariable String stockCode) {
        return generateDecisionResponse(stockCode);
    }

    /**
     * AI decision creation.
     * POST /api/ai/decisions?stockCode=005930
     */
    @PostMapping("/decisions")
    public ApiResponse<Map<String, Object>> generateDecisionByQueryParam(@RequestParam String stockCode) {
        return generateDecisionResponse(stockCode);
    }

    /**
     * AI decision list.
     * GET /api/ai/decisions?stockCode=005930&limit=10
     */
    @GetMapping("/decisions")
    public ApiResponse<List<AiDecision>> getDecisions(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.ok(aiDecisionService.getDecisions(stockCode, limit));
    }

    /**
     * AI decision detail.
     * GET /api/ai/decisions/{id}
     */
    @GetMapping("/decisions/{id}")
    public ApiResponse<AiDecision> getDecision(@PathVariable Long id) {
        return aiDecisionService.getDecision(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("AI decision not found: id=" + id));
    }

    private ApiResponse<Map<String, Object>> generateDecisionResponse(String stockCode) {
        AiDecision decision = aiDecisionService.generateDecision(stockCode);
        if (decision == null) {
            return ApiResponse.error("AI decision creation failed");
        }
        return ApiResponse.ok("AI decision created", Map.of(
                "id", decision.getId(),
                "stockCode", decision.getStockCode(),
                "decision", decision.getDecision(),
                "confidence", decision.getConfidence()
        ));
    }
}
