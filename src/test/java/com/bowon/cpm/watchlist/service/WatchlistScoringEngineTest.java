package com.bowon.cpm.watchlist.service;

import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.domain.StockCandidateScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistScoringEngineTest {

    private final WatchlistScoringEngine engine = new WatchlistScoringEngine();

    @Test
    @DisplayName("liquid positive technical/news candidate gets high score")
    void highQualityCandidate() {
        StockCandidateMetrics metrics = StockCandidateMetrics.builder()
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .corpCode("00126380")
                .latestTradeDate(LocalDate.of(2026, 6, 5))
                .closePrice(BigDecimal.valueOf(80000))
                .previousClosePrice(BigDecimal.valueOf(79000))
                .tradingValue(BigDecimal.valueOf(7_000_000_000L))
                .ma5(BigDecimal.valueOf(79500))
                .ma20(BigDecimal.valueOf(76000))
                .ma60(BigDecimal.valueOf(72000))
                .rsi14(BigDecimal.valueOf(62))
                .volumeChangeRate(BigDecimal.valueOf(0.25))
                .averageSentimentScore(BigDecimal.valueOf(0.7))
                .averageImpactScore(BigDecimal.valueOf(0.8))
                .recentNewsCount(5)
                .recentMajorEventCount(0)
                .financialStatementCount(12)
                .fundamentalTotalScore(BigDecimal.valueOf(18))
                .roe(BigDecimal.valueOf(16.5))
                .per(BigDecimal.valueOf(9.2))
                .pbr(BigDecimal.valueOf(1.1))
                .psr(BigDecimal.valueOf(1.8))
                .build();

        StockCandidateScore score = engine.score(metrics, LocalDate.of(2026, 6, 6));

        assertThat(score.getScore()).isGreaterThan(BigDecimal.valueOf(80));
        assertThat(score.getLiquidityScore()).isEqualByComparingTo("20.0000");
        assertThat(score.getCandidateStatus()).isEqualTo("CANDIDATE");
        assertThat(score.getReason()).contains("technical=");
        assertThat(score.getReason()).contains("roe=");
    }

    @Test
    @DisplayName("missing market data candidate stays low but still scoreable")
    void missingDataCandidate() {
        StockCandidateMetrics metrics = StockCandidateMetrics.builder()
                .stockCode("000001")
                .stockName("No Data")
                .corpCode("00000001")
                .recentNewsCount(0)
                .recentMajorEventCount(0)
                .financialStatementCount(0)
                .build();

        StockCandidateScore score = engine.score(metrics, LocalDate.of(2026, 6, 6));

        assertThat(score.getScore()).isBetween(BigDecimal.valueOf(15), BigDecimal.valueOf(30));
        assertThat(score.getLiquidityScore()).isEqualByComparingTo("0.0000");
    }
}
