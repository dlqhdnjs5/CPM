package com.bowon.cpm.fundamental.service;

import com.bowon.cpm.dart.domain.DartStockQuantity;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.market.domain.StockPriceDaily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FundamentalIndicatorCalculatorTest {

    private final FundamentalIndicatorCalculator calculator = new FundamentalIndicatorCalculator();

    @Test
    @DisplayName("calculates valuation and profitability ratios from financials, shares, and close price")
    void calculateRatios() {
        FundamentalIndicatorCalculator.FinancialSnapshot current =
                new FundamentalIndicatorCalculator.FinancialSnapshot(
                        2025,
                        "11011",
                        "00126380",
                        new BigDecimal("1000000000"),
                        new BigDecimal("150000000"),
                        new BigDecimal("100000000"),
                        new BigDecimal("2000000000"),
                        new BigDecimal("700000000"),
                        new BigDecimal("1300000000")
                );
        FundamentalIndicatorCalculator.FinancialSnapshot previous =
                new FundamentalIndicatorCalculator.FinancialSnapshot(
                        2024,
                        "11011",
                        "00126380",
                        new BigDecimal("800000000"),
                        new BigDecimal("100000000"),
                        new BigDecimal("70000000"),
                        new BigDecimal("1800000000"),
                        new BigDecimal("650000000"),
                        new BigDecimal("1150000000")
                );
        DartStockQuantity quantity = DartStockQuantity.builder()
                .issuedStockQuantity(1_000_000L)
                .distributedStockQuantity(900_000L)
                .build();
        StockPriceDaily price = StockPriceDaily.builder()
                .stockCode("005930")
                .tradeDate(LocalDate.of(2026, 6, 5))
                .closePrice(new BigDecimal("1000"))
                .build();

        StockFundamentalIndicator indicator = calculator.calculate("005930", current, previous, quantity, price);

        assertThat(indicator.getMarketCap()).isEqualByComparingTo("1000000000.0000");
        assertThat(indicator.getPer()).isEqualByComparingTo("10.000000");
        assertThat(indicator.getPbr()).isEqualByComparingTo("0.769231");
        assertThat(indicator.getPsr()).isEqualByComparingTo("1.000000");
        assertThat(indicator.getRoe()).isEqualByComparingTo("7.692308");
        assertThat(indicator.getDebtRatio()).isEqualByComparingTo("53.846154");
        assertThat(indicator.getRevenueGrowthRate()).isEqualByComparingTo("25.000000");
        assertThat(indicator.getTotalScore()).isGreaterThan(BigDecimal.ZERO);
    }
}
