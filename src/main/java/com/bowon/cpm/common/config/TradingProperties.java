package com.bowon.cpm.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cpm.trading")
public record TradingProperties(
        String mode,
        boolean enabled
) {
    public String normalizedMode() {
        return mode == null || mode.isBlank() ? "PAPER" : mode.trim().toUpperCase();
    }

    public boolean isPaperMode() {
        return "PAPER".equals(normalizedMode());
    }

    public boolean isRealMode() {
        return "REAL".equals(normalizedMode());
    }

    public boolean isRealOrderEnabled() {
        return isRealMode() && enabled;
    }
}
