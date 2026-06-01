package com.bowon.cpm.broker.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PortfolioPositionResult {
    private String stockCode;
    private String stockName;
    private Integer quantity;
    private Integer availableQuantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal valuationAmount;
    private BigDecimal profitLossAmount;
    private BigDecimal profitLossRate;
}

