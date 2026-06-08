package com.bowon.cpm.fundamental.service;

import com.bowon.cpm.dart.domain.DartStockQuantity;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.market.domain.StockPriceDaily;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class FundamentalIndicatorCalculator {

    private static final int RATIO_SCALE = 6;
    private static final int AMOUNT_SCALE = 4;
    private static final int SCORE_SCALE = 4;

    public StockFundamentalIndicator calculate(
            String stockCode,
            FinancialSnapshot current,
            FinancialSnapshot previous,
            DartStockQuantity stockQuantity,
            StockPriceDaily latestPrice
    ) {
        BigDecimal closePrice = latestPrice != null ? latestPrice.getClosePrice() : null;
        Long issuedShares = stockQuantity != null ? stockQuantity.getIssuedStockQuantity() : null;
        Long distributedShares = stockQuantity != null ? stockQuantity.getDistributedStockQuantity() : null;

        BigDecimal marketCap = multiply(closePrice, issuedShares);
        BigDecimal freeFloatMarketCap = multiply(closePrice, distributedShares);

        BigDecimal per = multiple(marketCap, current.netIncome());
        BigDecimal pbr = multiple(marketCap, current.totalEquity());
        BigDecimal psr = multiple(marketCap, current.revenue());

        BigDecimal roe = percentage(current.netIncome(), current.totalEquity());
        BigDecimal roa = percentage(current.netIncome(), current.totalAssets());
        BigDecimal debtRatio = percentage(current.totalLiabilities(), current.totalEquity());
        BigDecimal operatingMargin = percentage(current.operatingIncome(), current.revenue());
        BigDecimal netMargin = percentage(current.netIncome(), current.revenue());

        BigDecimal revenueGrowthRate = previous != null ? growthRate(current.revenue(), previous.revenue()) : null;
        BigDecimal operatingIncomeGrowthRate = previous != null ? growthRate(current.operatingIncome(), previous.operatingIncome()) : null;
        BigDecimal netIncomeGrowthRate = previous != null ? growthRate(current.netIncome(), previous.netIncome()) : null;

        BigDecimal profitabilityScore = scoreProfitability(roe, operatingMargin, netMargin);
        BigDecimal stabilityScore = scoreStability(debtRatio);
        BigDecimal growthScore = scoreGrowth(revenueGrowthRate, operatingIncomeGrowthRate, netIncomeGrowthRate);
        BigDecimal valuationScore = scoreValuation(per, pbr, psr);
        BigDecimal totalScore = score(profitabilityScore.doubleValue()
                + stabilityScore.doubleValue()
                + growthScore.doubleValue()
                + valuationScore.doubleValue());

        return StockFundamentalIndicator.builder()
                .stockCode(stockCode)
                .corpCode(current.corpCode())
                .businessYear(current.businessYear())
                .reportCode(current.reportCode())
                .baseDate(latestPrice != null ? latestPrice.getTradeDate() : null)
                .closePrice(closePrice)
                .issuedShares(issuedShares)
                .distributedShares(distributedShares)
                .marketCap(marketCap)
                .freeFloatMarketCap(freeFloatMarketCap)
                .revenue(current.revenue())
                .operatingIncome(current.operatingIncome())
                .netIncome(current.netIncome())
                .totalAssets(current.totalAssets())
                .totalLiabilities(current.totalLiabilities())
                .totalEquity(current.totalEquity())
                .per(per)
                .pbr(pbr)
                .psr(psr)
                .roe(roe)
                .roa(roa)
                .debtRatio(debtRatio)
                .operatingMargin(operatingMargin)
                .netMargin(netMargin)
                .revenueGrowthRate(revenueGrowthRate)
                .operatingIncomeGrowthRate(operatingIncomeGrowthRate)
                .netIncomeGrowthRate(netIncomeGrowthRate)
                .profitabilityScore(profitabilityScore)
                .stabilityScore(stabilityScore)
                .growthScore(growthScore)
                .valuationScore(valuationScore)
                .totalScore(totalScore)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private BigDecimal multiply(BigDecimal price, Long quantity) {
        if (price == null || quantity == null || quantity <= 0 || price.signum() <= 0) {
            return null;
        }
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal multiple(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || numerator.signum() <= 0 || denominator.signum() <= 0) {
            return null;
        }
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal growthRate(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreProfitability(BigDecimal roe, BigDecimal operatingMargin, BigDecimal netMargin) {
        double value = linear(roe, 0, 15, 3.0)
                + linear(operatingMargin, 0, 15, 2.0)
                + linear(netMargin, 0, 10, 1.0);
        return score(value);
    }

    private BigDecimal scoreStability(BigDecimal debtRatio) {
        if (debtRatio == null) {
            return score(0);
        }
        double value = debtRatio.doubleValue();
        if (value <= 50) {
            return score(4.0);
        }
        if (value <= 100) {
            return score(3.5);
        }
        if (value <= 150) {
            return score(2.5);
        }
        if (value <= 200) {
            return score(1.5);
        }
        if (value <= 300) {
            return score(0.5);
        }
        return score(0);
    }

    private BigDecimal scoreGrowth(BigDecimal revenueGrowth, BigDecimal operatingIncomeGrowth, BigDecimal netIncomeGrowth) {
        double value = linear(revenueGrowth, 0, 20, 1.5)
                + linear(operatingIncomeGrowth, 0, 20, 1.25)
                + linear(netIncomeGrowth, 0, 20, 1.25);
        return score(value);
    }

    private BigDecimal scoreValuation(BigDecimal per, BigDecimal pbr, BigDecimal psr) {
        double value = 0;
        value += band(per, new double[][]{{8, 2.0}, {15, 1.5}, {25, 1.0}, {40, 0.4}});
        value += band(pbr, new double[][]{{0.8, 2.0}, {1.5, 1.5}, {2.5, 0.8}, {4.0, 0.3}});
        value += band(psr, new double[][]{{1.0, 2.0}, {2.5, 1.2}, {5.0, 0.5}});
        return score(value);
    }

    private double linear(BigDecimal raw, double min, double max, double maxScore) {
        if (raw == null) {
            return 0;
        }
        double value = raw.doubleValue();
        if (value <= min) {
            return 0;
        }
        if (value >= max) {
            return maxScore;
        }
        return (value - min) / (max - min) * maxScore;
    }

    private double band(BigDecimal raw, double[][] bands) {
        if (raw == null || raw.signum() <= 0) {
            return 0;
        }
        double value = raw.doubleValue();
        for (double[] band : bands) {
            if (value <= band[0]) {
                return band[1];
            }
        }
        return 0;
    }

    private BigDecimal score(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(20, value))).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    public record FinancialSnapshot(
            Integer businessYear,
            String reportCode,
            String corpCode,
            BigDecimal revenue,
            BigDecimal operatingIncome,
            BigDecimal netIncome,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity
    ) {
    }
}
