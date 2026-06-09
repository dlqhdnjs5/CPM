package com.bowon.cpm.ai.prompt;

import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the OpenAI decision prompt from normalized decision features.
 */
@Component
public class AiDecisionPromptBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal LOW_VOLUME_RATIO = new BigDecimal("0.20");
    private static final BigDecimal HIGH_VOLUME_RATIO = new BigDecimal("3.00");
    private static final BigDecimal LOW_QUALITY_PRICE_GAP_RATE = new BigDecimal("5.00");
    private static final BigDecimal INCOMPLETE_LATEST_VOLUME_RATIO = new BigDecimal("0.05");
    private static final int TOP_NEWS_LIMIT = 5;

    public String buildSystemPrompt() {
        return """
                You are the AI decision engine for a Korean stock auto-trading system.

                Return only valid JSON that matches the required response schema.
                Do not return markdown, explanations, or code fences.
                Write all natural-language response fields in Korean, including reason,
                analysis.* values, and factors[].summary. Keep enum/code values in English,
                such as BUY, SELL, HOLD, LOW, MEDIUM, HIGH, TECHNICAL, NEWS, DART,
                FUNDAMENTAL, SUPPLY_DEMAND, POSITIVE, NEGATIVE, and NEUTRAL.

                Use the provided inputJson as the source of truth. Missing fields are null.
                Be conservative: when data quality is LOW, important data is missing, or the
                evidence is mixed, return HOLD with lower confidence.

                Decision rules:
                - decision must be one of BUY, SELL, HOLD.
                - If inputJson.priceDataQuality.quality is LOW, do not return BUY or SELL.
                - If inputJson.position.isHolding is false, do not return SELL.
                - If inputJson.position.isHolding is true, evaluate HOLD, SELL, or cautious additional BUY.
                - If latestVolumeAbnormal is true or currentPriceDailyCloseGapRate is high, lower confidence.
                - Positive news alone is not enough for BUY when technical trend, supply/demand, or data quality is weak.
                - Good fundamentals with weak short-term data should usually be HOLD.
                - Use disclosureSummary event flags instead of over-weighting repeated disclosure titles.
                - recommendedPortfolioWeight must stay within account.totalAsset and account.availableCash constraints.
                - If a field is unavailable, keep the corresponding analysis cautious rather than inventing data.
                """;
    }

    public String buildUserPrompt(
            String stockCode,
            String stockName,
            List<StockPriceDaily> dailyPrices,
            List<StockNews> newsList,
            List<DartDisclosure> disclosures,
            BigDecimal totalAsset,
            BigDecimal availableCash,
            List<AiFeedback> recentFeedbacks,
            String financialSummary,
            List<DartMajorEvent> majorEvents,
            StockIndicatorDaily indicator,
            BigDecimal realtimeQuote,
            AiPeriodicSummary weeklySummary,
            AiPeriodicSummary monthlySummary,
            PortfolioPosition position,
            StockFundamentalIndicator fundamentalIndicator,
            String tradingMode
    ) {
        Map<String, Object> inputJson = buildInputJson(
                stockCode,
                stockName,
                safeList(dailyPrices),
                safeList(newsList),
                safeList(disclosures),
                totalAsset,
                availableCash,
                safeList(recentFeedbacks),
                financialSummary,
                safeList(majorEvents),
                indicator,
                realtimeQuote,
                weeklySummary,
                monthlySummary,
                position,
                fundamentalIndicator,
                tradingMode
        );

        return "Input JSON:\n" + toJson(inputJson);
    }

    public String buildUserPrompt(
            String stockCode,
            String stockName,
            List<StockPriceDaily> dailyPrices,
            List<StockNews> newsList,
            List<DartDisclosure> disclosures,
            BigDecimal totalAsset,
            BigDecimal availableCash,
            List<AiFeedback> recentFeedbacks,
            String financialSummary,
            List<DartMajorEvent> majorEvents,
            StockIndicatorDaily indicator,
            BigDecimal realtimeQuote,
            AiPeriodicSummary weeklySummary,
            AiPeriodicSummary monthlySummary
    ) {
        return buildUserPrompt(stockCode, stockName, dailyPrices, newsList, disclosures,
                totalAsset, availableCash, recentFeedbacks, financialSummary, majorEvents,
                indicator, realtimeQuote, weeklySummary, monthlySummary,
                null, null, null);
    }

    public String buildUserPrompt(
            String stockCode,
            String stockName,
            List<StockPriceDaily> dailyPrices,
            List<StockNews> newsList,
            List<DartDisclosure> disclosures,
            BigDecimal totalAsset,
            BigDecimal availableCash,
            List<AiFeedback> recentFeedbacks,
            String financialSummary,
            List<DartMajorEvent> majorEvents,
            StockIndicatorDaily indicator,
            BigDecimal realtimeQuote
    ) {
        return buildUserPrompt(stockCode, stockName, dailyPrices, newsList, disclosures,
                totalAsset, availableCash, recentFeedbacks, financialSummary,
                majorEvents, indicator, realtimeQuote, null, null);
    }

    private Map<String, Object> buildInputJson(
            String stockCode,
            String stockName,
            List<StockPriceDaily> dailyPrices,
            List<StockNews> newsList,
            List<DartDisclosure> disclosures,
            BigDecimal totalAsset,
            BigDecimal availableCash,
            List<AiFeedback> recentFeedbacks,
            String financialSummary,
            List<DartMajorEvent> majorEvents,
            StockIndicatorDaily indicator,
            BigDecimal realtimeQuote,
            AiPeriodicSummary weeklySummary,
            AiPeriodicSummary monthlySummary,
            PortfolioPosition position,
            StockFundamentalIndicator fundamentalIndicator,
            String tradingMode
    ) {
        List<StockPriceDaily> effectiveDailyPrices = effectiveDailyPrices(dailyPrices, realtimeQuote);
        StockPriceDaily latestPrice = latestDailyPrice(effectiveDailyPrices);
        BigDecimal dailyClose = latestPrice != null ? latestPrice.getClosePrice() : null;
        BigDecimal currentPrice = realtimeQuote != null ? realtimeQuote : dailyClose;
        BigDecimal volumeRatio20 = calculateVolumeRatio20(effectiveDailyPrices);
        BigDecimal currentPriceDailyCloseGapRate = calculateGapRate(currentPrice, dailyClose);
        boolean excludedLatestDailyPrice = effectiveDailyPrices.size() < dailyPrices.size();
        StockIndicatorDaily effectiveIndicator = effectiveIndicator(indicator, latestPrice);

        Map<String, Object> root = orderedMap();
        root.put("stock", stockSection(stockCode, stockName, currentPrice, latestPrice));
        root.put("account", accountSection(totalAsset, availableCash, tradingMode));
        root.put("position", positionSection(position));
        root.put("technical", technicalSection(effectiveIndicator, volumeRatio20));
        root.put("priceDataQuality", priceDataQualitySection(
                effectiveDailyPrices,
                effectiveIndicator,
                currentPrice,
                dailyClose,
                currentPriceDailyCloseGapRate,
                volumeRatio20,
                excludedLatestDailyPrice
        ));
        root.put("marketContext", emptyMarketContext());
        root.put("newsSummary", newsSummarySection(newsList));
        root.put("disclosureSummary", disclosureSummarySection(disclosures, majorEvents));
        root.put("supplyDemand", emptySupplyDemand());
        root.put("fundamental", fundamentalSection(fundamentalIndicator, financialSummary));
        root.put("riskReward", riskRewardSection(effectiveDailyPrices, currentPrice));
        root.put("strategyFeedback", strategyFeedbackSection(recentFeedbacks, weeklySummary, monthlySummary));
        return root;
    }

    private Map<String, Object> stockSection(
            String stockCode,
            String stockName,
            BigDecimal currentPrice,
            StockPriceDaily latestPrice
    ) {
        Map<String, Object> section = orderedMap();
        section.put("code", stockCode);
        section.put("name", stockName);
        section.put("currentPrice", currentPrice);
        section.put("latestDailyTradeDate", latestPrice != null && latestPrice.getTradeDate() != null
                ? latestPrice.getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
        section.put("latestDailyClosePrice", latestPrice != null ? latestPrice.getClosePrice() : null);
        return section;
    }

    private Map<String, Object> accountSection(BigDecimal totalAsset, BigDecimal availableCash, String tradingMode) {
        Map<String, Object> section = orderedMap();
        section.put("tradingMode", blankToNull(tradingMode));
        section.put("totalAsset", totalAsset);
        section.put("availableCash", availableCash);
        section.put("cashRatio", ratio(availableCash, totalAsset));
        return section;
    }

    private Map<String, Object> positionSection(PortfolioPosition position) {
        Integer quantity = position != null ? position.getQuantity() : null;
        boolean isHolding = quantity != null && quantity > 0;

        Map<String, Object> section = orderedMap();
        section.put("isHolding", isHolding);
        section.put("quantity", isHolding ? quantity : 0);
        section.put("availableQuantity", isHolding ? position.getAvailableQuantity() : 0);
        section.put("averagePrice", isHolding ? position.getAverageBuyPrice() : null);
        section.put("currentPrice", isHolding ? position.getCurrentPrice() : null);
        section.put("valuationAmount", isHolding ? position.getValuationAmount() : null);
        section.put("unrealizedProfitRate", isHolding ? position.getProfitLossRate() : null);
        section.put("holdingDays", null);
        return section;
    }

    private Map<String, Object> technicalSection(StockIndicatorDaily indicator, BigDecimal volumeRatio20) {
        Map<String, Object> section = orderedMap();
        section.put("tradeDate", indicator != null && indicator.getTradeDate() != null
                ? indicator.getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
        section.put("ma5", indicator != null ? indicator.getMa5() : null);
        section.put("ma20", indicator != null ? indicator.getMa20() : null);
        section.put("ma60", indicator != null ? indicator.getMa60() : null);
        section.put("ma120", indicator != null ? indicator.getMa120() : null);
        section.put("rsi14", indicator != null ? indicator.getRsi14() : null);
        section.put("macd", indicator != null ? indicator.getMacd() : null);
        section.put("macdSignal", indicator != null ? indicator.getMacdSignal() : null);
        section.put("macdHistogram", indicator != null ? indicator.getMacdHistogram() : null);
        section.put("macdDirection", indicator != null ? macdDirection(indicator.getMacdHistogram()) : null);
        section.put("bollingerUpper", indicator != null ? indicator.getBollingerUpper() : null);
        section.put("bollingerMiddle", indicator != null ? indicator.getBollingerMiddle() : null);
        section.put("bollingerLower", indicator != null ? indicator.getBollingerLower() : null);
        section.put("volatility", indicator != null ? indicator.getVolatility() : null);
        section.put("volumeChangeRate", indicator != null ? indicator.getVolumeChangeRate() : null);
        section.put("volumeRatio20", volumeRatio20);
        return section;
    }

    private Map<String, Object> priceDataQualitySection(
            List<StockPriceDaily> dailyPrices,
            StockIndicatorDaily indicator,
            BigDecimal currentPrice,
            BigDecimal dailyClose,
            BigDecimal currentPriceDailyCloseGapRate,
            BigDecimal volumeRatio20,
            boolean excludedLatestDailyPrice
    ) {
        boolean latestVolumeAbnormal = isLatestVolumeAbnormal(volumeRatio20);
        String quality = priceDataQuality(dailyPrices, indicator, currentPrice, dailyClose,
                currentPriceDailyCloseGapRate, volumeRatio20);

        Map<String, Object> section = orderedMap();
        section.put("currentPriceDailyCloseGapRate", currentPriceDailyCloseGapRate);
        section.put("latestVolumeAbnormal", latestVolumeAbnormal);
        section.put("quality", quality);
        section.put("dailyPriceCount", dailyPrices.size());
        section.put("hasLatestIndicator", indicator != null);
        section.put("excludedLatestDailyPrice", excludedLatestDailyPrice);
        return section;
    }

    private Map<String, Object> emptyMarketContext() {
        Map<String, Object> section = orderedMap();
        section.put("kospiChangeRate", null);
        section.put("kosdaqChangeRate", null);
        section.put("sectorChangeRate", null);
        section.put("soxIndexChangeRate", null);
        section.put("usdKrwChangeRate", null);
        return section;
    }

    private Map<String, Object> newsSummarySection(List<StockNews> newsList) {
        int positiveCount = 0;
        int negativeCount = 0;
        int neutralCount = 0;
        BigDecimal sentimentNumerator = ZERO;
        BigDecimal sentimentWeight = ZERO;
        BigDecimal impactTotal = ZERO;
        int impactCount = 0;

        for (StockNews news : newsList) {
            String sentiment = normalize(news.getSentiment());
            if ("POSITIVE".equals(sentiment)) {
                positiveCount++;
            } else if ("NEGATIVE".equals(sentiment)) {
                negativeCount++;
            } else {
                neutralCount++;
            }

            BigDecimal impact = positiveOrDefault(news.getImpactScore(), BigDecimal.ONE);
            if (news.getSentimentScore() != null) {
                sentimentNumerator = sentimentNumerator.add(news.getSentimentScore().multiply(impact));
                sentimentWeight = sentimentWeight.add(impact);
            }
            if (news.getImpactScore() != null) {
                impactTotal = impactTotal.add(news.getImpactScore());
                impactCount++;
            }
        }

        List<Map<String, Object>> topNews = newsList.stream()
                .sorted(Comparator
                        .comparing((StockNews n) -> n.getImpactScore() != null ? n.getImpactScore() : ZERO)
                        .reversed()
                        .thenComparing((StockNews n) -> n.getPublishedAt() != null ? n.getPublishedAt() : null,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_NEWS_LIMIT)
                .map(this::newsItem)
                .toList();

        Map<String, Object> section = orderedMap();
        section.put("weightedSentimentScore", divide(sentimentNumerator, sentimentWeight));
        section.put("positiveCount", positiveCount);
        section.put("negativeCount", negativeCount);
        section.put("neutralCount", neutralCount);
        section.put("averageImpactScore", impactCount > 0
                ? impactTotal.divide(BigDecimal.valueOf(impactCount), 6, RoundingMode.HALF_UP) : null);
        section.put("topNews", topNews);
        return section;
    }

    private Map<String, Object> newsItem(StockNews news) {
        Map<String, Object> item = orderedMap();
        item.put("date", news.getPublishedAt() != null
                ? news.getPublishedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
        item.put("title", news.getTitle());
        item.put("sentiment", normalize(news.getSentiment()));
        item.put("sentimentScore", news.getSentimentScore());
        item.put("impactScore", news.getImpactScore());
        item.put("summary", news.getAiSummary() != null ? news.getAiSummary() : news.getSummary());
        return item;
    }

    private Map<String, Object> disclosureSummarySection(
            List<DartDisclosure> disclosures,
            List<DartMajorEvent> majorEvents
    ) {
        boolean hasEarnings = containsDisclosure(disclosures,
                "earnings", "profit",
                "\uC7A0\uC815\uC2E4\uC801", "\uC601\uC5C5\uC2E4\uC801", "\uC190\uC775\uAD6C\uC870")
                || containsMajorEvent(majorEvents, "EARNINGS");
        boolean hasLargeContract = containsMajorEvent(majorEvents, "SINGLE_CONTRACT")
                || containsDisclosure(disclosures,
                "contract",
                "\uB2E8\uC77C\uD310\uB9E4", "\uACF5\uAE09\uACC4\uC57D", "\uACC4\uC57D\uCCB4\uACB0");
        boolean hasInsiderOwnershipChange = containsDisclosure(disclosures,
                "insider", "director", "officer",
                "\uC784\uC6D0", "\uC8FC\uC694\uC8FC\uC8FC", "\uC18C\uC720\uC8FC\uC2DD");
        boolean hasMajorShareholderChange = containsMajorEvent(majorEvents, "MAJOR_SHAREHOLDER")
                || containsDisclosure(disclosures,
                "major shareholder",
                "\uCD5C\uB300\uC8FC\uC8FC", "\uB300\uC8FC\uC8FC", "\uB300\uB7C9\uBCF4\uC720");
        boolean hasRiskDisclosure = containsDisclosure(disclosures,
                "trading suspension", "delisting", "lawsuit", "embezzlement", "breach of trust", "audit opinion",
                "\uAC70\uB798\uC815\uC9C0", "\uC0C1\uC7A5\uD3D0\uC9C0", "\uC18C\uC1A1",
                "\uD6A1\uB839", "\uBC30\uC784", "\uAC10\uC0AC\uC758\uACAC", "\uAD00\uB9AC\uC885\uBAA9",
                "\uD22C\uC790\uC704\uD5D8");

        String latestImportantDisclosure = latestImportantDisclosure(disclosures, majorEvents);
        String disclosureImpact = hasRiskDisclosure ? "NEGATIVE"
                : (hasLargeContract || hasEarnings ? "POSITIVE" : "NEUTRAL");

        Map<String, Object> section = orderedMap();
        section.put("hasEarnings", hasEarnings);
        section.put("hasLargeContract", hasLargeContract);
        section.put("hasInsiderOwnershipChange", hasInsiderOwnershipChange);
        section.put("hasMajorShareholderChange", hasMajorShareholderChange);
        section.put("hasRiskDisclosure", hasRiskDisclosure);
        section.put("latestImportantDisclosure", latestImportantDisclosure);
        section.put("disclosureImpact", disclosureImpact);
        return section;
    }

    private Map<String, Object> emptySupplyDemand() {
        Map<String, Object> section = orderedMap();
        section.put("foreignNetBuyAmount", null);
        section.put("institutionNetBuyAmount", null);
        section.put("individualNetBuyAmount", null);
        section.put("shortSellingAmount", null);
        section.put("shortSellingRatio", null);
        return section;
    }

    private Map<String, Object> fundamentalSection(
            StockFundamentalIndicator indicator,
            String financialSummary
    ) {
        Map<String, Object> section = orderedMap();
        section.put("businessYear", indicator != null ? indicator.getBusinessYear() : null);
        section.put("per", indicator != null ? indicator.getPer() : null);
        section.put("pbr", indicator != null ? indicator.getPbr() : null);
        section.put("psr", indicator != null ? indicator.getPsr() : null);
        section.put("roe", indicator != null ? indicator.getRoe() : null);
        section.put("debtRatio", indicator != null ? indicator.getDebtRatio() : null);
        section.put("operatingMargin", indicator != null ? indicator.getOperatingMargin() : null);
        section.put("netMargin", indicator != null ? indicator.getNetMargin() : null);
        section.put("revenueGrowth", indicator != null ? indicator.getRevenueGrowthRate() : null);
        section.put("assetGrowth", null);
        section.put("netIncomeGrowth", indicator != null ? indicator.getNetIncomeGrowthRate() : null);
        section.put("profitabilityImproved", profitabilityImproved(indicator));
        section.put("debtRisk", debtRisk(indicator != null ? indicator.getDebtRatio() : null));
        section.put("fundamentalScore", indicator != null ? indicator.getTotalScore() : null);
        section.put("summary", blankToNull(limitText(financialSummary, 500)));
        return section;
    }

    private Map<String, Object> riskRewardSection(List<StockPriceDaily> dailyPrices, BigDecimal currentPrice) {
        BigDecimal resistance = dailyPrices.stream()
                .map(StockPriceDaily::getHighPrice)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal support = dailyPrices.stream()
                .map(StockPriceDaily::getLowPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal expectedReturnRate = calculateSignedRate(resistance, currentPrice);
        BigDecimal expectedLossRate = calculateSignedRate(support, currentPrice);

        Map<String, Object> section = orderedMap();
        section.put("expectedReturnRate", expectedReturnRate);
        section.put("expectedLossRate", expectedLossRate);
        section.put("riskRewardRatio", riskRewardRatio(expectedReturnRate, expectedLossRate));
        section.put("recentResistancePrice", resistance);
        section.put("recentSupportPrice", support);
        return section;
    }

    private Map<String, Object> strategyFeedbackSection(
            List<AiFeedback> recentFeedbacks,
            AiPeriodicSummary weeklySummary,
            AiPeriodicSummary monthlySummary
    ) {
        Map<String, Object> section = orderedMap();
        section.put("recentModelBias", inferModelBias(weeklySummary, monthlySummary));
        section.put("recentFeedbackCount", recentFeedbacks.size());
        section.put("latestFeedbackSummary", recentFeedbacks.stream()
                .map(AiFeedback::getFeedbackSummary)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(value -> limitText(value, 300))
                .orElse(null));
        section.put("weeklySummaryBrief", summaryBrief(weeklySummary));
        section.put("monthlySummaryBrief", summaryBrief(monthlySummary));
        return section;
    }

    private StockPriceDaily latestDailyPrice(List<StockPriceDaily> dailyPrices) {
        return dailyPrices.stream()
                .filter(price -> price.getTradeDate() != null)
                .max(Comparator.comparing(StockPriceDaily::getTradeDate))
                .orElse(dailyPrices.isEmpty() ? null : dailyPrices.get(0));
    }

    private List<StockPriceDaily> effectiveDailyPrices(List<StockPriceDaily> dailyPrices, BigDecimal realtimeQuote) {
        if (dailyPrices.size() < 2 || realtimeQuote == null) {
            return dailyPrices;
        }
        StockPriceDaily latest = latestDailyPrice(dailyPrices);
        if (latest == null || !looksIncompleteLatestDailyPrice(dailyPrices, latest, realtimeQuote)) {
            return dailyPrices;
        }
        return dailyPrices.stream()
                .filter(price -> price != latest)
                .toList();
    }

    private boolean looksIncompleteLatestDailyPrice(
            List<StockPriceDaily> dailyPrices,
            StockPriceDaily latest,
            BigDecimal realtimeQuote
    ) {
        BigDecimal latestClose = latest.getClosePrice();
        BigDecimal gapRate = calculateGapRate(realtimeQuote, latestClose);
        BigDecimal latestVolumeRatio = calculateLatestVolumeRatio(dailyPrices, latest, 20);
        return gapRate != null
                && gapRate.compareTo(LOW_QUALITY_PRICE_GAP_RATE) > 0
                && latestVolumeRatio != null
                && latestVolumeRatio.compareTo(INCOMPLETE_LATEST_VOLUME_RATIO) < 0;
    }

    private BigDecimal calculateLatestVolumeRatio(
            List<StockPriceDaily> dailyPrices,
            StockPriceDaily latest,
            int period
    ) {
        if (latest.getVolume() == null || latest.getVolume() <= 0) {
            return null;
        }
        double average = dailyPrices.stream()
                .filter(price -> price != latest)
                .filter(price -> price.getVolume() != null && price.getVolume() > 0)
                .sorted(Comparator.comparing(StockPriceDaily::getTradeDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(period)
                .mapToLong(StockPriceDaily::getVolume)
                .average()
                .orElse(0);
        if (average <= 0) {
            return null;
        }
        return BigDecimal.valueOf(latest.getVolume())
                .divide(BigDecimal.valueOf(average), 6, RoundingMode.HALF_UP);
    }

    private StockIndicatorDaily effectiveIndicator(StockIndicatorDaily indicator, StockPriceDaily latestPrice) {
        if (indicator == null || latestPrice == null
                || indicator.getTradeDate() == null || latestPrice.getTradeDate() == null) {
            return indicator;
        }
        return indicator.getTradeDate().isAfter(latestPrice.getTradeDate()) ? null : indicator;
    }

    private BigDecimal calculateVolumeRatio20(List<StockPriceDaily> dailyPrices) {
        if (dailyPrices.size() < 2) {
            return null;
        }
        StockPriceDaily latest = latestDailyPrice(dailyPrices);
        if (latest == null || latest.getVolume() == null || latest.getVolume() <= 0) {
            return null;
        }
        double average = dailyPrices.stream()
                .filter(price -> price != latest)
                .filter(price -> price.getVolume() != null && price.getVolume() > 0)
                .sorted(Comparator.comparing(StockPriceDaily::getTradeDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .mapToLong(StockPriceDaily::getVolume)
                .average()
                .orElse(0);
        if (average <= 0) {
            return null;
        }
        return BigDecimal.valueOf(latest.getVolume())
                .divide(BigDecimal.valueOf(average), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateGapRate(BigDecimal currentPrice, BigDecimal dailyClose) {
        if (currentPrice == null || dailyClose == null || dailyClose.signum() <= 0) {
            return null;
        }
        return currentPrice.subtract(dailyClose).abs()
                .multiply(ONE_HUNDRED)
                .divide(dailyClose, 4, RoundingMode.HALF_UP);
    }

    private String priceDataQuality(
            List<StockPriceDaily> dailyPrices,
            StockIndicatorDaily indicator,
            BigDecimal currentPrice,
            BigDecimal dailyClose,
            BigDecimal currentPriceDailyCloseGapRate,
            BigDecimal volumeRatio20
    ) {
        if (currentPrice == null || dailyClose == null || dailyPrices.isEmpty()) {
            return "LOW";
        }
        if (currentPriceDailyCloseGapRate != null
                && currentPriceDailyCloseGapRate.compareTo(LOW_QUALITY_PRICE_GAP_RATE) > 0) {
            return "LOW";
        }
        if (volumeRatio20 != null && volumeRatio20.compareTo(LOW_VOLUME_RATIO) < 0) {
            return "LOW";
        }
        if (indicator == null || dailyPrices.size() < 20 || isLatestVolumeAbnormal(volumeRatio20)) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private boolean isLatestVolumeAbnormal(BigDecimal volumeRatio20) {
        return volumeRatio20 != null
                && (volumeRatio20.compareTo(LOW_VOLUME_RATIO) < 0
                || volumeRatio20.compareTo(HIGH_VOLUME_RATIO) > 0);
    }

    private BigDecimal calculateSignedRate(BigDecimal target, BigDecimal currentPrice) {
        if (target == null || currentPrice == null || currentPrice.signum() <= 0) {
            return null;
        }
        return target.subtract(currentPrice)
                .multiply(ONE_HUNDRED)
                .divide(currentPrice, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal riskRewardRatio(BigDecimal expectedReturnRate, BigDecimal expectedLossRate) {
        if (expectedReturnRate == null || expectedLossRate == null
                || expectedReturnRate.signum() <= 0 || expectedLossRate.signum() >= 0) {
            return null;
        }
        return expectedReturnRate.divide(expectedLossRate.abs(), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveOrDefault(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }

    private String macdDirection(BigDecimal histogram) {
        if (histogram == null) {
            return null;
        }
        int sign = histogram.signum();
        if (sign > 0) {
            return "POSITIVE";
        }
        if (sign < 0) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private Boolean profitabilityImproved(StockFundamentalIndicator indicator) {
        if (indicator == null) {
            return null;
        }
        return isPositive(indicator.getOperatingIncomeGrowthRate())
                || isPositive(indicator.getNetIncomeGrowthRate())
                || isPositive(indicator.getRoe());
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String debtRisk(BigDecimal debtRatio) {
        if (debtRatio == null) {
            return null;
        }
        if (debtRatio.compareTo(new BigDecimal("100")) <= 0) {
            return "LOW";
        }
        if (debtRatio.compareTo(new BigDecimal("200")) <= 0) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private boolean containsMajorEvent(List<DartMajorEvent> events, String token) {
        String normalizedToken = normalize(token);
        return events.stream()
                .map(DartMajorEvent::getEventType)
                .map(this::normalize)
                .anyMatch(value -> value != null && value.contains(normalizedToken));
    }

    private boolean containsDisclosure(List<DartDisclosure> disclosures, String... tokens) {
        return disclosures.stream()
                .map(DartDisclosure::getReportName)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(name -> {
                    for (String token : tokens) {
                        String normalizedToken = normalize(token);
                        if (normalizedToken != null && name.contains(normalizedToken)) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    private String latestImportantDisclosure(List<DartDisclosure> disclosures, List<DartMajorEvent> majorEvents) {
        String major = majorEvents.stream()
                .sorted(Comparator.comparing(DartMajorEvent::getEventDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(event -> event.getSummary() != null ? event.getSummary() : event.getEventTitle())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (major != null) {
            return limitText(major, 300);
        }

        return disclosures.stream()
                .filter(disclosure -> Boolean.TRUE.equals(disclosure.getIsImportant()))
                .findFirst()
                .or(() -> disclosures.stream().findFirst())
                .map(DartDisclosure::getReportName)
                .map(value -> limitText(value, 300))
                .orElse(null);
    }

    private String inferModelBias(AiPeriodicSummary weeklySummary, AiPeriodicSummary monthlySummary) {
        String source = (textOf(weeklySummary) + " " + textOf(monthlySummary)).toUpperCase(Locale.ROOT);
        if (source.isBlank()) {
            return null;
        }
        if (source.contains("HOLD")) {
            return "HOLD_BIASED";
        }
        if (source.contains("BUY")) {
            return "BUY_BIASED";
        }
        if (source.contains("SELL")) {
            return "SELL_BIASED";
        }
        return null;
    }

    private String summaryBrief(AiPeriodicSummary summary) {
        if (summary == null || summary.getLlmSummary() == null || summary.getLlmSummary().isBlank()) {
            return null;
        }
        String period = "";
        if (summary.getPeriodStart() != null || summary.getPeriodEnd() != null) {
            period = "%s ~ %s: ".formatted(formatDate(summary.getPeriodStart()), formatDate(summary.getPeriodEnd()));
        }
        return limitText(period + summary.getLlmSummary().trim(), 500);
    }

    private String textOf(AiPeriodicSummary summary) {
        return summary != null && summary.getLlmSummary() != null ? summary.getLlmSummary() : "";
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ISO_LOCAL_DATE) : "?";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String limitText(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, limit);
    }

    private String toJson(Map<String, Object> inputJson) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(inputJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to build AI decision input JSON", e);
        }
    }

    private <T> List<T> safeList(List<T> value) {
        return value != null ? value : List.of();
    }

    private Map<String, Object> orderedMap() {
        return new LinkedHashMap<>();
    }
}
