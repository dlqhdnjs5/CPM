package com.bowon.cpm.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cpm.liquidity")
public record LiquidityProperties(
        long minAverageVolume20,
        long minAverageTradingValue20
) {
    public LiquidityProperties {
        if (minAverageVolume20 <= 0) {
            minAverageVolume20 = 100_000L;
        }
        if (minAverageTradingValue20 <= 0) {
            minAverageTradingValue20 = 3_000_000_000L;
        }
    }
}
