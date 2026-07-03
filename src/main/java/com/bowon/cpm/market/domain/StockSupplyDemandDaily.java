package com.bowon.cpm.market.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSupplyDemandDaily {
    private Long id;
    private String stockCode;
    private LocalDate tradeDate;
    private BigDecimal closePrice;
    private Long individualNetBuyQty;
    private Long foreignNetBuyQty;
    private Long institutionNetBuyQty;
    private BigDecimal individualNetBuyAmount;
    private BigDecimal foreignNetBuyAmount;
    private BigDecimal institutionNetBuyAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
