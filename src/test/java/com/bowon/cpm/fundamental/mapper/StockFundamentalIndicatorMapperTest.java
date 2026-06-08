package com.bowon.cpm.fundamental.mapper;

import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.support.TestProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ActiveProfiles(TestProfiles.TEST)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockFundamentalIndicatorMapperTest {

    @Autowired StockFundamentalIndicatorMapper mapper;

    @Test
    @DisplayName("upserts fundamental indicator and finds latest by stock code")
    void upsertAndFindLatest() {
        mapper.upsert(StockFundamentalIndicator.builder()
                .stockCode("999991")
                .corpCode("99999991")
                .businessYear(2025)
                .reportCode("11011")
                .baseDate(LocalDate.of(2026, 6, 5))
                .closePrice(new BigDecimal("12000"))
                .issuedShares(1_000_000L)
                .distributedShares(900_000L)
                .marketCap(new BigDecimal("12000000000"))
                .freeFloatMarketCap(new BigDecimal("10800000000"))
                .revenue(new BigDecimal("1000000000"))
                .operatingIncome(new BigDecimal("120000000"))
                .netIncome(new BigDecimal("100000000"))
                .totalAssets(new BigDecimal("2000000000"))
                .totalLiabilities(new BigDecimal("800000000"))
                .totalEquity(new BigDecimal("1200000000"))
                .per(new BigDecimal("9.100000"))
                .pbr(new BigDecimal("1.200000"))
                .psr(new BigDecimal("1.800000"))
                .roe(new BigDecimal("12.500000"))
                .roa(new BigDecimal("5.000000"))
                .debtRatio(new BigDecimal("66.600000"))
                .operatingMargin(new BigDecimal("12.000000"))
                .netMargin(new BigDecimal("10.000000"))
                .revenueGrowthRate(new BigDecimal("8.000000"))
                .operatingIncomeGrowthRate(new BigDecimal("5.000000"))
                .netIncomeGrowthRate(new BigDecimal("4.000000"))
                .profitabilityScore(new BigDecimal("5.0000"))
                .stabilityScore(new BigDecimal("3.5000"))
                .growthScore(new BigDecimal("2.0000"))
                .valuationScore(new BigDecimal("4.0000"))
                .totalScore(new BigDecimal("14.5000"))
                .build());

        StockFundamentalIndicator latest = mapper.findLatestByStockCode("999991").orElseThrow();
        assertThat(latest.getTotalScore()).isEqualByComparingTo("14.5000");
        assertThat(latest.getRoe()).isEqualByComparingTo("12.500000");
        assertThat(mapper.findByStockCode("999991", 10)).isNotEmpty();
    }
}
