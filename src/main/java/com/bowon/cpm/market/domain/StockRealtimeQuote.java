package com.bowon.cpm.market.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class StockRealtimeQuote {
    private Long id;
    private String stockCode;
    private LocalDateTime quoteTime;
    private BigDecimal currentPrice;
    private BigDecimal changePrice;
    private BigDecimal changeRate;
    private Long volume;
    private Long accumulatedVolume;
    private BigDecimal tradingValue;
}

