package com.bowon.cpm.dashboard.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class DashboardAiTradeHistoryItem {
    private Long aiDecisionId;
    private String stockCode;
    private String stockName;
    private String decision;
    private BigDecimal confidence;
    private BigDecimal currentPrice;
    private BigDecimal targetPrice;
    private BigDecimal stopLossPrice;
    private BigDecimal recommendedPortfolioWeight;
    private String riskLevel;
    private String decisionStatus;
    private String reason;
    private LocalDateTime decisionCreatedAt;

    private Long riskCheckId;
    private Boolean riskPassed;
    private String riskFailReason;
    private LocalDateTime riskCheckedAt;

    private Long orderRequestId;
    private String orderSide;
    private String orderStatus;
    private Integer orderQuantity;
    private BigDecimal orderAmount;
    private String brokerType;
    private LocalDateTime orderRequestedAt;
}
