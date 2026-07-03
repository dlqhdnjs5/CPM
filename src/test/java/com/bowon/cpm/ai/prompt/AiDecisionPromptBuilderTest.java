package com.bowon.cpm.ai.prompt;

import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.market.domain.MarketContext;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.domain.StockSupplyDemandDaily;
import com.bowon.cpm.macro.domain.MacroContext;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiDecisionPromptBuilderTest {

    private final AiDecisionPromptBuilder builder = new AiDecisionPromptBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("system prompt keeps enums in English and natural-language fields in Korean")
    void system_prompt_requires_korean_explanations_with_english_enums() {
        String prompt = builder.buildSystemPrompt();
        String normalizedPrompt = prompt.replaceAll("\\s+", " ");

        assertThat(prompt).contains("Write all natural-language response fields in Korean");
        assertThat(prompt).contains("reason");
        assertThat(prompt).contains("analysis.*");
        assertThat(prompt).contains("factors[].summary");
        assertThat(prompt).contains("Keep enum/code values in English");
        assertThat(prompt).contains("BUY, SELL, HOLD");
        assertThat(prompt).contains("macroContext");
        assertThat(prompt).contains("market-wide liquidity");
        assertThat(normalizedPrompt).contains("stock.currentPrice as the market price source of truth");
        assertThat(normalizedPrompt).contains("recommendedPortfolioWeight must be 0.0");
        assertThat(normalizedPrompt).contains("riskReward.riskRewardRatio is a reference ratio");
        assertThat(normalizedPrompt).contains("BUY is still allowed");
        assertThat(normalizedPrompt).contains("breakout momentum");
        assertThat(normalizedPrompt).contains("0.03 to 0.10");
        assertThat(normalizedPrompt).contains("priceDataQuality.quality is MEDIUM");
        assertThat(normalizedPrompt).contains("Missing supplyDemand should be neutral");
        assertThat(normalizedPrompt).contains("do not infer news sentiment");
        assertThat(normalizedPrompt).contains("do not infer foreign, institution");
        assertThat(normalizedPrompt).contains("largeCurrentMove is true");
        assertThat(normalizedPrompt).contains("Do not add fields outside the schema");
    }

    @Test
    @DisplayName("user prompt is built as structured decision input JSON")
    void user_prompt_is_structured_json() throws Exception {
        String prompt = builder.buildUserPrompt(
                "005930",
                "Samsung Electronics",
                sampleDailyPrices(),
                sampleNews(),
                List.of(),
                new BigDecimal("10000000"),
                new BigDecimal("2500000"),
                List.of(AiFeedback.builder()
                        .evaluationType("HOLDING_END")
                        .feedbackSummary("Recent BUY decisions worked better after a pullback.")
                        .build()),
                "Revenue grew and debt remained manageable.",
                List.of(),
                StockIndicatorDaily.builder()
                        .tradeDate(LocalDate.of(2026, 6, 8))
                        .ma5(new BigDecimal("334300"))
                        .ma20(new BigDecimal("304125"))
                        .rsi14(new BigDecimal("53.93"))
                        .macd(new BigDecimal("10.0"))
                        .macdSignal(new BigDecimal("7.0"))
                        .macdHistogram(new BigDecimal("3.0"))
                        .bollingerUpper(new BigDecimal("357390"))
                        .bollingerMiddle(new BigDecimal("304125"))
                        .bollingerLower(new BigDecimal("250859"))
                        .volatility(new BigDecimal("5.15"))
                        .build(),
                new BigDecimal("105000"),
                AiPeriodicSummary.builder()
                        .summaryType("WEEKLY")
                        .periodStart(LocalDate.of(2026, 6, 1))
                        .periodEnd(LocalDate.of(2026, 6, 5))
                        .llmSummary("HOLD decisions were too frequent last week.")
                        .build(),
                null,
                PortfolioPosition.builder()
                        .stockCode("005930")
                        .quantity(3)
                        .availableQuantity(2)
                        .averageBuyPrice(new BigDecimal("95000"))
                        .currentPrice(new BigDecimal("105000"))
                        .profitLossRate(new BigDecimal("10.5263"))
                        .valuationAmount(new BigDecimal("315000"))
                        .build(),
                StockFundamentalIndicator.builder()
                        .businessYear(2025)
                        .per(new BigDecimal("12.5"))
                        .pbr(new BigDecimal("1.1"))
                        .psr(new BigDecimal("2.3"))
                        .roe(new BigDecimal("8.4"))
                        .debtRatio(new BigDecimal("72.1"))
                        .revenueGrowthRate(new BigDecimal("11.0"))
                        .netIncomeGrowthRate(new BigDecimal("4.2"))
                        .totalScore(new BigDecimal("13.5"))
                        .build(),
                "PAPER"
        );

        JsonNode root = parseInputJson(prompt);

        assertThat(root.path("stock").path("code").asText()).isEqualTo("005930");
        assertThat(root.path("account").path("cashRatio").decimalValue())
                .isEqualByComparingTo(new BigDecimal("0.250000"));
        assertThat(root.path("position").path("isHolding").asBoolean()).isTrue();
        assertThat(root.path("technical").path("macdDirection").asText()).isEqualTo("POSITIVE");
        assertThat(root.path("newsSummary").path("positiveCount").asInt()).isEqualTo(1);
        assertThat(root.path("fundamental").path("per").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(root.path("riskReward").path("basis").asText())
                .isEqualTo("RECENT_SUPPORT_RESISTANCE_REFERENCE");
        assertThat(root.path("riskReward").path("breakoutTargetAllowed").asBoolean()).isTrue();
        assertThat(root.path("strategyFeedback").path("recentModelBias").asText()).isEqualTo("HOLD_BIASED");
    }

    @Test
    @DisplayName("latest confirmed supply demand is included in prompt")
    void supply_demand_is_included() throws Exception {
        String prompt = builder.buildUserPrompt(
                "005930", "Samsung Electronics",
                sampleDailyPrices(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), null, List.of(),
                StockIndicatorDaily.builder()
                        .tradeDate(LocalDate.of(2026, 6, 8))
                        .ma5(new BigDecimal("100000"))
                        .ma20(new BigDecimal("99000"))
                        .rsi14(new BigDecimal("50"))
                        .build(),
                new BigDecimal("101000"),
                null, null,
                null, null,
                MarketContext.builder()
                        .kospiChangeRate(new BigDecimal("1.2300"))
                        .kosdaqChangeRate(new BigDecimal("-0.4500"))
                        .sectorChangeRate(new BigDecimal("2.1000"))
                        .marketType("KOSPI")
                        .sectorName("반도체")
                        .build(),
                null,
                StockSupplyDemandDaily.builder()
                        .stockCode("005930")
                        .tradeDate(LocalDate.of(2026, 6, 10))
                        .foreignNetBuyAmount(new BigDecimal("1200000000"))
                        .institutionNetBuyAmount(new BigDecimal("800000000"))
                        .individualNetBuyAmount(new BigDecimal("-2000000000"))
                        .foreignNetBuyQty(300000L)
                        .institutionNetBuyQty(200000L)
                        .individualNetBuyQty(-500000L)
                        .build(),
                "PAPER"
        );

        JsonNode root = parseInputJson(prompt);
        JsonNode supplyDemand = root.path("supplyDemand");
        JsonNode marketContext = root.path("marketContext");

        assertThat(marketContext.path("kospiChangeRate").decimalValue()).isEqualByComparingTo("1.2300");
        assertThat(marketContext.path("sectorName").asText()).isEqualTo("반도체");
        assertThat(supplyDemand.path("tradeDate").asText()).isEqualTo("2026-06-10");
        assertThat(supplyDemand.path("period").asText()).isEqualTo("LATEST_CONFIRMED");
        assertThat(supplyDemand.path("amountUnit").asText()).isEqualTo("KRW");
        assertThat(supplyDemand.path("foreignNetBuyAmount").decimalValue()).isEqualByComparingTo("1200000000");
        assertThat(supplyDemand.path("shortSellingAmount").isNull()).isTrue();
    }

    @Test
    @DisplayName("large realtime move is exposed as market movement, not low data quality")
    void large_realtime_move_is_market_movement_not_low_quality() throws Exception {
        String prompt = builder.buildUserPrompt(
                "005930", "Samsung Electronics",
                sampleDailyPrices(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), null, List.of(),
                StockIndicatorDaily.builder()
                        .tradeDate(LocalDate.of(2026, 6, 8))
                        .ma5(new BigDecimal("100000"))
                        .ma20(new BigDecimal("99000"))
                        .rsi14(new BigDecimal("50"))
                        .build(),
                new BigDecimal("120000"),
                null, null
        );

        JsonNode root = parseInputJson(prompt);
        JsonNode quality = root.path("priceDataQuality");
        JsonNode marketMove = root.path("marketMove");

        assertThat(quality.path("quality").asText()).isEqualTo("HIGH");
        assertThat(marketMove.path("currentVsLatestCloseRate").decimalValue())
                .isGreaterThan(new BigDecimal("5.0"));
        assertThat(marketMove.path("largeCurrentMove").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("incomplete latest daily candle is excluded from AI price quality input")
    void incomplete_latest_daily_candle_is_excluded() throws Exception {
        List<StockPriceDaily> prices = samsungPricesWithIncompleteLatest();

        String prompt = builder.buildUserPrompt(
                "005930", "Samsung Electronics",
                prices, List.of(), List.of(),
                new BigDecimal("3000000"), new BigDecimal("3000000"),
                List.of(), null, List.of(),
                StockIndicatorDaily.builder()
                        .tradeDate(LocalDate.of(2026, 6, 5))
                        .ma5(new BigDecimal("334300"))
                        .ma20(new BigDecimal("304125"))
                        .rsi14(new BigDecimal("53.93"))
                        .build(),
                new BigDecimal("322000"),
                null, null
        );

        JsonNode root = parseInputJson(prompt);
        JsonNode stock = root.path("stock");
        JsonNode quality = root.path("priceDataQuality");
        JsonNode marketMove = root.path("marketMove");

        assertThat(stock.path("latestDailyTradeDate").asText()).isEqualTo("2026-06-05");
        assertThat(stock.path("latestDailyClosePrice").decimalValue()).isEqualByComparingTo("335000.00");
        assertThat(quality.path("excludedLatestDailyPrice").asBoolean()).isTrue();
        assertThat(quality.path("quality").asText()).isEqualTo("HIGH");
        assertThat(marketMove.path("currentVsLatestCloseRate").decimalValue())
                .isGreaterThan(new BigDecimal("-5.0"))
                .isLessThan(new BigDecimal("0"));
        assertThat(marketMove.path("largeCurrentMove").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("missing position becomes non-holding so the model is instructed not to sell")
    void missing_position_is_non_holding() throws Exception {
        String prompt = builder.buildUserPrompt(
                "005930", "Samsung Electronics",
                List.of(), List.of(), List.of(),
                null, null,
                List.of(), null, List.of(),
                null, null,
                null, null
        );

        JsonNode position = parseInputJson(prompt).path("position");

        assertThat(position.path("isHolding").asBoolean()).isFalse();
        assertThat(position.path("quantity").asInt()).isZero();
        assertThat(parseInputJson(prompt).path("priceDataQuality").path("quality").asText()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("macro context is included as a separate market-wide input section")
    void macro_context_is_included() throws Exception {
        MacroContext macroContext = new MacroContext(
                new MacroContext.MacroSignal(
                        "MACRO_FED",
                        "HAWKISH",
                        new BigDecimal("-0.4"),
                        new BigDecimal("0.8"),
                        2,
                        List.of(new MacroContext.MacroNewsItem(
                                "Fed keeps rates higher for longer",
                                "연준의 매파적 발언으로 금리 부담이 커졌습니다.",
                                "NEGATIVE",
                                new BigDecimal("-0.6"),
                                new BigDecimal("0.9"),
                                LocalDateTime.of(2026, 6, 9, 22, 0)
                        )),
                        "MACRO_FED stance=HAWKISH"
                ),
                new MacroContext.MacroSignal("MACRO_BOK", "NEUTRAL", BigDecimal.ZERO,
                        new BigDecimal("0.3"), 1, List.of(), "MACRO_BOK stance=NEUTRAL"),
                new MacroContext.MacroSignal("MACRO_MARKET", "RISK_OFF", new BigDecimal("-0.2"),
                        new BigDecimal("0.5"), 1, List.of(), "MACRO_MARKET stance=RISK_OFF"),
                new BigDecimal("0.55"),
                LocalDateTime.of(2026, 6, 10, 1, 0)
        );

        String prompt = builder.buildUserPrompt(
                "005930", "Samsung Electronics",
                sampleDailyPrices(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), null, List.of(),
                StockIndicatorDaily.builder()
                        .tradeDate(LocalDate.of(2026, 6, 8))
                        .ma5(new BigDecimal("100000"))
                        .ma20(new BigDecimal("99000"))
                        .rsi14(new BigDecimal("50"))
                        .build(),
                new BigDecimal("101000"),
                null, null,
                null, null,
                null,
                macroContext,
                null,
                "PAPER"
        );

        JsonNode macro = parseInputJson(prompt).path("macroContext");

        assertThat(macro.path("fed").path("stance").asText()).isEqualTo("HAWKISH");
        assertThat(macro.path("fed").path("topNews").get(0).path("title").asText())
                .contains("Fed");
        assertThat(macro.path("combinedRiskScore").decimalValue()).isEqualByComparingTo("0.55");
    }

    private JsonNode parseInputJson(String prompt) throws Exception {
        int jsonStart = prompt.indexOf('{');
        assertThat(jsonStart).isGreaterThanOrEqualTo(0);
        return objectMapper.readTree(prompt.substring(jsonStart));
    }

    private List<StockPriceDaily> sampleDailyPrices() {
        List<StockPriceDaily> prices = new ArrayList<>();
        prices.add(StockPriceDaily.builder()
                .stockCode("005930")
                .tradeDate(LocalDate.of(2026, 6, 8))
                .openPrice(new BigDecimal("99000"))
                .highPrice(new BigDecimal("106000"))
                .lowPrice(new BigDecimal("98000"))
                .closePrice(new BigDecimal("100000"))
                .volume(1000L)
                .build());
        for (int i = 1; i <= 20; i++) {
            prices.add(StockPriceDaily.builder()
                    .stockCode("005930")
                    .tradeDate(LocalDate.of(2026, 6, 8).minusDays(i))
                    .openPrice(new BigDecimal("95000"))
                    .highPrice(new BigDecimal("105000"))
                    .lowPrice(new BigDecimal("90000"))
                    .closePrice(new BigDecimal("99000"))
                    .volume(1000L)
                    .build());
        }
        return prices;
    }

    private List<StockNews> sampleNews() {
        return List.of(
                StockNews.builder()
                        .title("AI memory demand improves")
                        .summary("Demand outlook improved.")
                        .sentiment("POSITIVE")
                        .sentimentScore(new BigDecimal("0.8"))
                        .impactScore(new BigDecimal("0.9"))
                        .publishedAt(LocalDateTime.of(2026, 6, 8, 9, 0))
                        .build(),
                StockNews.builder()
                        .title("Market waits for rates")
                        .summary("Neutral macro story.")
                        .sentiment("NEUTRAL")
                        .sentimentScore(new BigDecimal("0.0"))
                        .impactScore(new BigDecimal("0.2"))
                        .publishedAt(LocalDateTime.of(2026, 6, 7, 9, 0))
                        .build()
        );
    }

    private List<StockPriceDaily> samsungPricesWithIncompleteLatest() {
        List<StockPriceDaily> prices = new ArrayList<>();
        prices.add(StockPriceDaily.builder()
                .stockCode("005930")
                .tradeDate(LocalDate.of(2026, 6, 9))
                .openPrice(new BigDecimal("295500"))
                .highPrice(new BigDecimal("295500"))
                .lowPrice(new BigDecimal("295500"))
                .closePrice(new BigDecimal("295500"))
                .volume(577L)
                .build());
        prices.add(StockPriceDaily.builder()
                .stockCode("005930")
                .tradeDate(LocalDate.of(2026, 6, 5))
                .openPrice(new BigDecimal("333500"))
                .highPrice(new BigDecimal("343000"))
                .lowPrice(new BigDecimal("325000"))
                .closePrice(new BigDecimal("335000"))
                .volume(23921481L)
                .build());
        for (int i = 1; i <= 20; i++) {
            prices.add(StockPriceDaily.builder()
                    .stockCode("005930")
                    .tradeDate(LocalDate.of(2026, 6, 5).minusDays(i))
                    .openPrice(new BigDecimal("300000"))
                    .highPrice(new BigDecimal("340000"))
                    .lowPrice(new BigDecimal("290000"))
                    .closePrice(new BigDecimal("320000"))
                    .volume(30000000L)
                    .build());
        }
        return prices;
    }
}
