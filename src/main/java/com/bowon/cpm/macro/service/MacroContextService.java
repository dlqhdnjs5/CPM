package com.bowon.cpm.macro.service;

import com.bowon.cpm.macro.domain.MacroContext;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.news.mapper.StockNewsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MacroContextService {

    private static final int DEFAULT_DAYS = 7;
    private static final int DEFAULT_LIMIT_PER_SIGNAL = 20;
    private static final int TOP_NEWS_LIMIT = 5;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final StockNewsMapper stockNewsMapper;

    public MacroContext latestContext() {
        return latestContext(DEFAULT_DAYS, DEFAULT_LIMIT_PER_SIGNAL);
    }

    public MacroContext latestContext(int days, int limitPerSignal) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        MacroContext.MacroSignal fed = buildSignal(MacroNewsService.FED_CODE, since, limitPerSignal);
        MacroContext.MacroSignal bok = buildSignal(MacroNewsService.BOK_CODE, since, limitPerSignal);
        MacroContext.MacroSignal market = buildSignal(MacroNewsService.MARKET_CODE, since, limitPerSignal);

        return new MacroContext(
                fed,
                bok,
                market,
                combinedRiskScore(List.of(fed, bok, market)),
                LocalDateTime.now()
        );
    }

    private MacroContext.MacroSignal buildSignal(String code, LocalDateTime since, int limit) {
        List<StockNews> newsList = stockNewsMapper.findByStockCodeAndPublishedAfter(
                code,
                since,
                Math.max(1, limit)
        );

        BigDecimal weightedSentiment = weightedAverageSentiment(newsList);
        BigDecimal averageImpact = averageImpact(newsList);
        String stance = inferStance(newsList, weightedSentiment);
        List<MacroContext.MacroNewsItem> topNews = newsList.stream()
                .sorted(Comparator
                        .comparing((StockNews news) -> nullToZero(news.getImpactScore())).reversed()
                        .thenComparing(StockNews::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_NEWS_LIMIT)
                .map(this::toItem)
                .toList();

        return new MacroContext.MacroSignal(
                code,
                stance,
                weightedSentiment,
                averageImpact,
                newsList.size(),
                topNews,
                summarize(code, stance, weightedSentiment, averageImpact, newsList.size())
        );
    }

    private MacroContext.MacroNewsItem toItem(StockNews news) {
        return new MacroContext.MacroNewsItem(
                news.getTitle(),
                firstNonBlank(news.getAiSummary(), news.getSummary()),
                normalizeSentiment(news.getSentiment()),
                news.getSentimentScore(),
                news.getImpactScore(),
                news.getPublishedAt()
        );
    }

    private BigDecimal weightedAverageSentiment(List<StockNews> newsList) {
        BigDecimal numerator = ZERO;
        BigDecimal denominator = ZERO;
        for (StockNews news : newsList) {
            if (news.getSentimentScore() == null) {
                continue;
            }
            BigDecimal impact = news.getImpactScore() != null && news.getImpactScore().signum() > 0
                    ? news.getImpactScore()
                    : ONE;
            numerator = numerator.add(news.getSentimentScore().multiply(impact));
            denominator = denominator.add(impact);
        }
        if (denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal averageImpact(List<StockNews> newsList) {
        List<BigDecimal> impacts = newsList.stream()
                .map(StockNews::getImpactScore)
                .filter(Objects::nonNull)
                .toList();
        if (impacts.isEmpty()) {
            return null;
        }
        BigDecimal total = impacts.stream().reduce(ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(impacts.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal combinedRiskScore(List<MacroContext.MacroSignal> signals) {
        BigDecimal total = ZERO;
        BigDecimal weight = ZERO;
        for (MacroContext.MacroSignal signal : signals) {
            if (signal.impactScore() == null) {
                continue;
            }
            BigDecimal stanceRisk = switch (signal.stance()) {
                case "HAWKISH", "RISK_OFF" -> ONE;
                case "DOVISH", "RISK_ON" -> new BigDecimal("-1");
                default -> ZERO;
            };
            total = total.add(stanceRisk.multiply(signal.impactScore()));
            weight = weight.add(signal.impactScore());
        }
        if (weight.signum() == 0) {
            return null;
        }
        return total.divide(weight, 6, RoundingMode.HALF_UP);
    }

    private String inferStance(List<StockNews> newsList, BigDecimal weightedSentiment) {
        String joined = newsList.stream()
                .map(news -> String.join(" ",
                        nullToBlank(news.getTitle()),
                        nullToBlank(news.getSummary()),
                        nullToBlank(news.getAiSummary())))
                .collect(java.util.stream.Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);

        int hawkish = countAny(joined,
                "매파", "긴축", "금리 인상", "인플레이션 우려", "고금리", "higher for longer", "hawkish");
        int dovish = countAny(joined,
                "비둘기", "완화", "금리 인하", "경기 둔화", "rate cut", "dovish");
        int riskOff = countAny(joined,
                "급락", "침체", "위험 회피", "risk off", "환율 급등", "국채금리 급등");
        int riskOn = countAny(joined,
                "반등", "위험 선호", "risk on", "환율 안정", "국채금리 하락");

        if (hawkish + riskOff > dovish + riskOn) {
            return hawkish >= riskOff ? "HAWKISH" : "RISK_OFF";
        }
        if (dovish + riskOn > hawkish + riskOff) {
            return dovish >= riskOn ? "DOVISH" : "RISK_ON";
        }
        if (weightedSentiment != null) {
            if (weightedSentiment.compareTo(new BigDecimal("0.25")) >= 0) {
                return "RISK_ON";
            }
            if (weightedSentiment.compareTo(new BigDecimal("-0.25")) <= 0) {
                return "RISK_OFF";
            }
        }
        return "NEUTRAL";
    }

    private int countAny(String source, String... tokens) {
        int count = 0;
        for (String token : tokens) {
            if (source.contains(token.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private String summarize(String code, String stance, BigDecimal sentiment, BigDecimal impact, int newsCount) {
        if (newsCount == 0) {
            return code + " 관련 최근 분석 뉴스가 없습니다.";
        }
        return "%s stance=%s, newsCount=%d, sentiment=%s, impact=%s"
                .formatted(code, stance, newsCount, sentiment, impact);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private String normalizeSentiment(String value) {
        if (value == null || value.isBlank()) {
            return "NEUTRAL";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POSITIVE", "NEGATIVE", "NEUTRAL" -> normalized;
            default -> "NEUTRAL";
        };
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String nullToBlank(String value) {
        return value != null ? value : "";
    }
}
