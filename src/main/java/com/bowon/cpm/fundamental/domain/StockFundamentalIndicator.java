package com.bowon.cpm.fundamental.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockFundamentalIndicator {
    private Long id;
    private String stockCode;
    private String corpCode;
    private Integer businessYear;
    private String reportCode;
    private LocalDate baseDate;
    private BigDecimal closePrice;
    private Long issuedShares;
    private Long distributedShares;
    private BigDecimal marketCap;
    private BigDecimal freeFloatMarketCap;
    private BigDecimal revenue;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal per;
    private BigDecimal pbr;
    private BigDecimal psr;
    private BigDecimal roe;
    private BigDecimal roa;
    private BigDecimal debtRatio;
    private BigDecimal operatingMargin;
    private BigDecimal netMargin;
    private BigDecimal revenueGrowthRate;
    private BigDecimal operatingIncomeGrowthRate;
    private BigDecimal netIncomeGrowthRate;
    private BigDecimal profitabilityScore;
    private BigDecimal stabilityScore;
    private BigDecimal growthScore;
    private BigDecimal valuationScore;
    private BigDecimal totalScore;
    private LocalDateTime calculatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
