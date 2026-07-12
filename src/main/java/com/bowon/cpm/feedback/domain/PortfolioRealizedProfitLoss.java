package com.bowon.cpm.feedback.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PortfolioRealizedProfitLoss {
    @Setter
    private Long id;
    private Long orderExecutionId;
    private Long orderRequestId;
    private String brokerOrderNo;
    private String accountNo;
    private String stockCode;
    private Integer executedQuantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal executedPrice;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal realizedProfitLoss;
    private BigDecimal returnRate;
    private LocalDateTime realizedAt;
    private LocalDateTime createdAt;
}
