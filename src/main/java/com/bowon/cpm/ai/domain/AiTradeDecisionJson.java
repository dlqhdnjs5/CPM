package com.bowon.cpm.ai.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * OpenAI 응답 JSON 파싱 DTO
 * json_schema strict:true 응답에 맞춘 구조
 *
 * P3: analysis 객체 + factors 배열 추가
 *  - analysis: 영역별 분석 텍스트 (technical/news/disclosure/fundamental/supplyDemand)
 *  - factors:  판단에 영향을 준 요인 다건 (ai_decision_factor 저장용)
 *
 * 두 필드는 strict 스키마에서 required이지만 내부 값은 nullable 허용.
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
        String reason,
        Analysis analysis,
        List<FactorJson> factors
) {

    /** 영역별 분석 텍스트 (각 필드 null 허용) */
    public record Analysis(
            String technicalAnalysis,
            String newsAnalysis,
            String disclosureAnalysis,
            String fundamentalAnalysis,
            String supplyDemandAnalysis
    ) {
    }

    /** 판단에 영향을 준 요인 1건 */
    public record FactorJson(
            /** TECHNICAL / NEWS / DART / FUNDAMENTAL / SUPPLY_DEMAND */
            String type,
            /** POSITIVE / NEGATIVE / NEUTRAL */
            String direction,
            /** 0.0 ~ 1.0 */
            BigDecimal score,
            String summary
    ) {
    }
}

