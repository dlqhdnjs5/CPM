package com.bowon.cpm.market.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일봉 기술적 지표 (stock_indicator_daily 매핑)
 *
 * AI 판단 프롬프트에 활용. 데이터가 없으면 graceful 처리.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockIndicatorDaily {

    private Long id;
    private String stockCode;
    private LocalDate tradeDate;

    private BigDecimal ma5;
    private BigDecimal ma20;
    private BigDecimal ma60;
    private BigDecimal ma120;

    private BigDecimal rsi14;
    private BigDecimal macd;
    private BigDecimal macdSignal;
    private BigDecimal macdHistogram;

    private BigDecimal bollingerUpper;
    private BigDecimal bollingerMiddle;
    private BigDecimal bollingerLower;

    private BigDecimal volatility;
    private BigDecimal volumeChangeRate;

    private LocalDateTime createdAt;
}

