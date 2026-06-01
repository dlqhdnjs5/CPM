package com.bowon.cpm.market.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 종목 일봉 가격 이력
 * 테이블: stock_price_daily
 */
@Getter
@Builder
public class StockPriceDaily {
    private Long id;
    private String stockCode;
    private LocalDate tradeDate;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volume;
    private BigDecimal tradingValue;
    /** 데이터 출처 (기본값: KIS) */
    private String source;
}

