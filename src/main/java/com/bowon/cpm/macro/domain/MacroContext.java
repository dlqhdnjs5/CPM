package com.bowon.cpm.macro.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MacroContext(
        MacroSignal fed,
        MacroSignal bok,
        MacroSignal market,
        BigDecimal combinedRiskScore,
        LocalDateTime generatedAt
) {

    public record MacroSignal(
            String code,
            String stance,
            BigDecimal sentimentScore,
            BigDecimal impactScore,
            int newsCount,
            List<MacroNewsItem> topNews,
            String summary
    ) {}

    public record MacroNewsItem(
            String title,
            String summary,
            String sentiment,
            BigDecimal sentimentScore,
            BigDecimal impactScore,
            LocalDateTime publishedAt
    ) {}
}
