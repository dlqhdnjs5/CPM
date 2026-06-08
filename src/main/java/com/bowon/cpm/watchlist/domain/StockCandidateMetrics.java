package com.bowon.cpm.watchlist.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCandidateMetrics {
    private String stockCode;
    private String stockName;
    private String corpCode;
    private String marketType;
    private LocalDate latestTradeDate;
    private BigDecimal closePrice;
    private BigDecimal previousClosePrice;
    private BigDecimal tradingValue;
    private Long volume;
    private BigDecimal rsi14;
    private BigDecimal ma5;
    private BigDecimal ma20;
    private BigDecimal ma60;
    private BigDecimal volumeChangeRate;
    private BigDecimal volatility;
    private BigDecimal averageSentimentScore;
    private BigDecimal averageImpactScore;
    private Integer recentNewsCount;
    private Integer recentMajorEventCount;
    private Integer financialStatementCount;
    private BigDecimal fundamentalTotalScore;
    private BigDecimal profitabilityScore;
    private BigDecimal stabilityScore;
    private BigDecimal growthScore;
    private BigDecimal valuationScore;
    private BigDecimal per;
    private BigDecimal pbr;
    private BigDecimal psr;
    private BigDecimal roe;
    private BigDecimal roa;
    private BigDecimal debtRatio;
    private BigDecimal operatingMargin;
    private BigDecimal netMargin;
    private BigDecimal revenueGrowthRate;
}
