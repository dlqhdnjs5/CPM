package com.bowon.cpm.paper.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaperPortfolioPosition {
    private Long id;
    private String accountNo;
    private String stockCode;
    private String stockName;
    private Integer quantity;
    private Integer availableQuantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal purchaseAmount;
    private BigDecimal valuationAmount;
    private BigDecimal profitLossAmount;
    private BigDecimal profitLossRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
