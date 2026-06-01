package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.PortfolioProfitLoss;
import com.bowon.cpm.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * AI 판단 단건 피드백 생성
     * POST /api/feedback/decisions/{aiDecisionId}?type=DAILY
     *
     * 흐름: AI 판단 조회 → 현재가 조회 → 수익률/목표가/손절가 계산 → ai_feedback 저장
     */
    @PostMapping("/decisions/{aiDecisionId}")
    public ApiResponse<Map<String, Object>> evaluateDecision(
            @PathVariable Long aiDecisionId,
            @RequestParam(defaultValue = "DAILY") String type
    ) {
        AiFeedback feedback = feedbackService.evaluateDecision(aiDecisionId, type);
        return ApiResponse.ok("피드백 생성 완료", Map.of(
                "aiDecisionId", aiDecisionId,
                "evaluationType", type,
                "returnRate", feedback.getReturnRate() != null ? feedback.getReturnRate() : "N/A",
                "targetReached", feedback.getTargetReached() != null ? feedback.getTargetReached() : "N/A",
                "stopLossReached", feedback.getStopLossReached() != null ? feedback.getStopLossReached() : "N/A",
                "success", feedback.getSuccess() != null ? feedback.getSuccess() : "N/A",
                "feedbackSummary", feedback.getFeedbackSummary()
        ));
    }

    /**
     * AI 판단 피드백 조회
     * GET /api/feedback/decisions/{aiDecisionId}
     */
    @GetMapping("/decisions/{aiDecisionId}")
    public ApiResponse<List<AiFeedback>> getFeedbacks(@PathVariable Long aiDecisionId) {
        return ApiResponse.ok(feedbackService.getFeedbacks(aiDecisionId));
    }

    /**
     * 일간 포트폴리오 수익률 저장
     * POST /api/feedback/portfolio/daily
     *
     * account_balance 테이블의 최근 2건을 비교해 일간 수익률 계산 후 portfolio_profit_loss 저장
     */
    @PostMapping("/portfolio/daily")
    public ApiResponse<Map<String, Object>> saveDailyProfitLoss() {
        PortfolioProfitLoss result = feedbackService.saveDailyProfitLoss();
        return ApiResponse.ok("일간 수익률 저장 완료", Map.of(
                "baseDate", result.getBaseDate().toString(),
                "startAsset", result.getStartAssetAmount() != null ? result.getStartAssetAmount() : "N/A",
                "endAsset", result.getEndAssetAmount() != null ? result.getEndAssetAmount() : "N/A",
                "returnRate", result.getReturnRate() != null ? result.getReturnRate() : "N/A"
        ));
    }
}

