package com.bowon.cpm.support.fixture;

import com.bowon.cpm.ai.domain.AiDecision;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AiDecision 테스트 데이터 빌더. */
public final class AiDecisionFixture {

    private AiDecisionFixture() {}

    public static AiDecision.AiDecisionBuilder defaults() {
        return AiDecision.builder()
                .id(1L)
                .stockCode("TEST_001")
                .stockName("테스트종목")
                .decision("BUY")
                .confidence(new BigDecimal("0.85"))
                .currentPrice(new BigDecimal("10000"))
                .targetPrice(new BigDecimal("11000"))
                .stopLossPrice(new BigDecimal("9000"))
                .expectedReturnRate(new BigDecimal("10.00"))
                .expectedLossRate(new BigDecimal("-10.00"))
                .riskRewardRatio(new BigDecimal("1.00"))
                .recommendedPortfolioWeight(new BigDecimal("0.10"))
                .expectedHoldingDays(20)
                .riskLevel("MEDIUM")
                .reason("테스트")
                .decisionStatus("CREATED")
                .createdAt(LocalDateTime.now());
    }

    public static AiDecision buy(Long id, BigDecimal current, BigDecimal target, BigDecimal stopLoss) {
        return defaults()
                .id(id)
                .decision("BUY")
                .currentPrice(current)
                .targetPrice(target)
                .stopLossPrice(stopLoss)
                .build();
    }

    public static AiDecision sell(Long id, BigDecimal current, BigDecimal target, BigDecimal stopLoss) {
        return defaults()
                .id(id)
                .decision("SELL")
                .currentPrice(current)
                .targetPrice(target)
                .stopLossPrice(stopLoss)
                .build();
    }

    public static AiDecision buyMatured(Long id, int holdingDays, LocalDateTime createdAt) {
        return defaults()
                .id(id)
                .decision("BUY")
                .expectedHoldingDays(holdingDays)
                .createdAt(createdAt)
                .build();
    }
}

