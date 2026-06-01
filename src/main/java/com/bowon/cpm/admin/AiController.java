package com.bowon.cpm.admin;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.service.AiDecisionService;
import com.bowon.cpm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiDecisionService aiDecisionService;

    /**
     * AI 매매 판단 생성
     * POST /api/ai/decisions/{stockCode}
     */
    @PostMapping("/decisions/{stockCode}")
    public ApiResponse<Map<String, Object>> generateDecision(@PathVariable String stockCode) {
        AiDecision decision = aiDecisionService.generateDecision(stockCode);
        if (decision == null) {
            return ApiResponse.error("AI 판단 생성 실패 (파싱 오류 또는 응답 없음)");
        }
        return ApiResponse.ok("AI 판단 생성 완료", Map.of(
                "id", decision.getId(),
                "stockCode", decision.getStockCode(),
                "decision", decision.getDecision(),
                "confidence", decision.getConfidence()
        ));
    }

    /**
     * AI 판단 목록 조회
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
     * AI 판단 단건 조회
     * GET /api/ai/decisions/{id}
     */
    @GetMapping("/decisions/{id}")
    public ApiResponse<AiDecision> getDecision(@PathVariable Long id) {
        return aiDecisionService.getDecision(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("AI 판단 없음: id=" + id));
    }
}

