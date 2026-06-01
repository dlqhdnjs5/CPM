package com.bowon.cpm.ai.domain;

import java.math.BigDecimal;

/**
 * OpenAI 응답 JSON 파싱 DTO
 * json_schema strict:true 응답에 맞춘 구조
 */
public record AiTradeDecisionJson(
        String stockCode,
        String stockName,
        /** BUY / SELL / HOLD */
        String decision,
        /** 신뢰도 0.0 ~ 1.0 */
        BigDecimal confidence,
        BigDecimal currentPrice,
        BigDecimal targetPrice,
        BigDecimal stopLossPrice,
        BigDecimal expectedReturnRate,
        BigDecimal expectedLossRate,
        BigDecimal riskRewardRatio,
        /** 추천 포트폴리오 비중 0.0 ~ 1.0 */
        BigDecimal recommendedPortfolioWeight,
        Integer expectedHoldingDays,
        /** LOW / MEDIUM / HIGH */
        String riskLevel,
        String reason
) {
}

