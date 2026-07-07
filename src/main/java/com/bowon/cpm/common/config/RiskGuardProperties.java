package com.bowon.cpm.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "cpm.risk.guard")
public record RiskGuardProperties(
        BigDecimal maxRiskPerTradeRate,
        BigDecimal dailyLossLimitRate,
        int maxDailyStopLossCount,
        int sameStockStopLossCooldownHours,
        int maxRiskCheckAgeMinutes,
        BigDecimal marketLossBlockRate,
        BigDecimal sectorLossBlockRate,
        int realizedLossCooldownHours,
        int maxRecentRealizedLossCount,
        BigDecimal breakevenProtectBufferRate,
        BigDecimal maxBuyRsi14,
        BigDecimal maxMa20ExtensionRate,
        BigDecimal maxBollingerUpperExtensionRate,
        BigDecimal minCashReserveRate,
        int maxHeldPositionCount,
        BigDecimal maxSectorExposureRate
) {
    public RiskGuardProperties {
        if (maxRiskPerTradeRate == null) {
            maxRiskPerTradeRate = new BigDecimal("0.005");
        }
        if (dailyLossLimitRate == null) {
            dailyLossLimitRate = new BigDecimal("0.015");
        }
        if (maxDailyStopLossCount <= 0) {
            maxDailyStopLossCount = 2;
        }
        if (sameStockStopLossCooldownHours <= 0) {
            sameStockStopLossCooldownHours = 24;
        }
        if (maxRiskCheckAgeMinutes <= 0) {
            maxRiskCheckAgeMinutes = 10;
        }
        if (marketLossBlockRate == null) {
            marketLossBlockRate = new BigDecimal("-1.5");
        }
        if (sectorLossBlockRate == null) {
            sectorLossBlockRate = new BigDecimal("-2.5");
        }
        if (realizedLossCooldownHours <= 0) {
            realizedLossCooldownHours = 48;
        }
        if (maxRecentRealizedLossCount <= 0) {
            maxRecentRealizedLossCount = 3;
        }
        if (breakevenProtectBufferRate == null) {
            breakevenProtectBufferRate = new BigDecimal("0.002");
        }
        if (maxBuyRsi14 == null) {
            maxBuyRsi14 = new BigDecimal("75");
        }
        if (maxMa20ExtensionRate == null) {
            maxMa20ExtensionRate = new BigDecimal("0.12");
        }
        if (maxBollingerUpperExtensionRate == null) {
            maxBollingerUpperExtensionRate = new BigDecimal("0.03");
        }
        if (minCashReserveRate == null) {
            minCashReserveRate = new BigDecimal("0.20");
        }
        if (maxHeldPositionCount <= 0) {
            maxHeldPositionCount = 8;
        }
        if (maxSectorExposureRate == null) {
            maxSectorExposureRate = new BigDecimal("0.35");
        }
    }
}
