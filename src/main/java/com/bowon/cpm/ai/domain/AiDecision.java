package com.bowon.cpm.ai.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 테이블: ai_decision */
@Getter @Builder
public class AiDecision {
    @Setter
    private Long id;
    private String stockCode;
    private String stockName;
    /** BUY / SELL / HOLD */
    private String decision;
    private BigDecimal confidence;
    private BigDecimal currentPrice;
    private BigDecimal targetPrice;
    private BigDecimal stopLossPrice;
    private BigDecimal expectedReturnRate;
    private BigDecimal expectedLossRate;
    private BigDecimal riskRewardRatio;
    private BigDecimal recommendedPortfolioWeight;
    private Integer expectedHoldingDays;
    /** LOW / MEDIUM / HIGH */
    private String riskLevel;
    private String reason;
    private Long rawResponseId;
    /** CREATED → RISK_PASSED / RISK_FAILED → ORDERED / EXPIRED */
    private String decisionStatus;
    private LocalDateTime createdAt;
}
