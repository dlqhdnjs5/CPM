package com.bowon.cpm.watchlist.service;

import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.domain.StockCandidateScore;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class WatchlistScoringEngine {

    private static final BigDecimal ONE_BILLION = BigDecimal.valueOf(1_000_000_000L);

    public StockCandidateScore score(StockCandidateMetrics metrics, LocalDate scoredDate) {
        double liquidity = liquidityScore(metrics);
        double technical = technicalScore(metrics);
        double news = newsScore(metrics);
        double dart = dartScore(metrics);
        double fundamental = fundamentalScore(metrics);
        double risk = riskScore(metrics);
        double total = clamp(liquidity + technical + news + dart + fundamental + risk, 0, 100);

        return StockCandidateScore.builder()
                .stockCode(metrics.getStockCode())
                .stockName(metrics.getStockName())
                .corpCode(metrics.getCorpCode())
                .score(decimal(total))
                .liquidityScore(decimal(liquidity))
                .technicalScore(decimal(technical))
                .newsScore(decimal(news))
                .dartScore(decimal(dart))
                .fundamentalScore(decimal(fundamental))
                .riskScore(decimal(risk))
                .reason(reason(metrics, liquidity, technical, news, dart, fundamental, risk))
                .candidateStatus("CANDIDATE")
                .scoredDate(scoredDate)
                .build();
    }

    private double liquidityScore(StockCandidateMetrics metrics) {
        BigDecimal tradingValue = metrics.getTradingValue();
        if (tradingValue == null || tradingValue.signum() <= 0) {
            return 0;
        }
        double billionUnits = tradingValue.divide(ONE_BILLION, 6, RoundingMode.HALF_UP).doubleValue();
        return clamp(billionUnits * 4, 0, 20);
    }

    private double technicalScore(StockCandidateMetrics metrics) {
        double score = 0;
        BigDecimal close = metrics.getClosePrice();
        BigDecimal previousClose = metrics.getPreviousClosePrice();
        BigDecimal ma5 = metrics.getMa5();
        BigDecimal ma20 = metrics.getMa20();
        BigDecimal ma60 = metrics.getMa60();
        BigDecimal rsi = metrics.getRsi14();
        BigDecimal volumeChangeRate = metrics.getVolumeChangeRate();

        if (positive(close) && positive(previousClose) && close.compareTo(previousClose) > 0) {
            score += 4;
        }
        if (positive(close) && positive(ma20) && close.compareTo(ma20) > 0) {
            score += 6;
        }
        if (positive(ma5) && positive(ma20) && ma5.compareTo(ma20) > 0) {
            score += 7;
        }
        if (positive(ma20) && positive(ma60) && ma20.compareTo(ma60) > 0) {
            score += 6;
        }
        if (positive(rsi)) {
            double rsiValue = rsi.doubleValue();
            if (rsiValue >= 45 && rsiValue <= 70) {
                score += 5;
            } else if (rsiValue > 35 && rsiValue < 80) {
                score += 2;
            }
        }
        if (volumeChangeRate != null && volumeChangeRate.doubleValue() > 0) {
            score += 2;
        }
        return clamp(score, 0, 25);
    }

    private double newsScore(StockCandidateMetrics metrics) {
        int newsCount = metrics.getRecentNewsCount() != null ? metrics.getRecentNewsCount() : 0;
        if (newsCount == 0) {
            return 4;
        }

        double sentiment = normalizedSentiment(metrics.getAverageSentimentScore());
        double impact = normalizedZeroToOne(metrics.getAverageImpactScore());
        double countBonus = Math.min(4, newsCount);
        return clamp(sentiment * 7 + impact * 4 + countBonus, 0, 15);
    }

    private double dartScore(StockCandidateMetrics metrics) {
        int eventCount = metrics.getRecentMajorEventCount() != null ? metrics.getRecentMajorEventCount() : 0;
        return clamp(10 - eventCount * 1.5, 3, 10);
    }

    private double fundamentalScore(StockCandidateMetrics metrics) {
        if (metrics.getFundamentalTotalScore() != null) {
            return clamp(metrics.getFundamentalTotalScore().doubleValue(), 0, 20);
        }
        int statementCount = metrics.getFinancialStatementCount() != null ? metrics.getFinancialStatementCount() : 0;
        return clamp(statementCount * 0.35, 0, 5);
    }

    private double riskScore(StockCandidateMetrics metrics) {
        double score = 10;
        if (metrics.getRsi14() != null) {
            double rsi = metrics.getRsi14().doubleValue();
            if (rsi >= 80) {
                score -= 5;
            } else if (rsi >= 72) {
                score -= 2;
            }
        }
        if (metrics.getVolatility() != null && metrics.getVolatility().doubleValue() >= 0.12) {
            score -= 2;
        }
        return clamp(score, 0, 10);
    }

    private String reason(StockCandidateMetrics metrics,
                          double liquidity,
                          double technical,
                          double news,
                          double dart,
                          double fundamental,
                          double risk) {
        List<String> parts = new ArrayList<>();
        parts.add("liquidity=" + decimal(liquidity));
        parts.add("technical=" + decimal(technical));
        parts.add("news=" + decimal(news));
        parts.add("dart=" + decimal(dart));
        parts.add("fundamental=" + decimal(fundamental));
        parts.add("risk=" + decimal(risk));
        if (metrics.getRoe() != null) {
            parts.add("roe=" + metrics.getRoe());
        }
        if (metrics.getPer() != null) {
            parts.add("per=" + metrics.getPer());
        }
        if (metrics.getPbr() != null) {
            parts.add("pbr=" + metrics.getPbr());
        }
        if (metrics.getPsr() != null) {
            parts.add("psr=" + metrics.getPsr());
        }
        if (metrics.getDebtRatio() != null) {
            parts.add("debtRatio=" + metrics.getDebtRatio());
        }
        if (metrics.getLatestTradeDate() != null) {
            parts.add("latestTradeDate=" + metrics.getLatestTradeDate());
        }
        return String.join(", ", parts);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private double normalizedSentiment(BigDecimal value) {
        if (value == null) {
            return 0.5;
        }
        double raw = value.doubleValue();
        if (raw >= -1 && raw <= 1) {
            return clamp((raw + 1) / 2, 0, 1);
        }
        return normalizedZeroToOne(value);
    }

    private double normalizedZeroToOne(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        double raw = value.doubleValue();
        if (raw > 1) {
            raw = raw / 100;
        }
        return clamp(raw, 0, 1);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
