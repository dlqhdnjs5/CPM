package com.bowon.cpm.ai.prompt;

import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
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

        assertThat(prompt).contains("Write all natural-language response fields in Korean");
        assertThat(prompt).contains("reason");
        assertThat(prompt).contains("analysis.*");
        assertThat(prompt).contains("factors[].summary");
        assertThat(prompt).contains("Keep enum/code values in English");
        assertThat(prompt).contains("BUY, SELL, HOLD");
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
        assertThat(root.path("strategyFeedback").path("recentModelBias").asText()).isEqualTo("HOLD_BIASED");
    }

    @Test
    @DisplayName("low price data quality is exposed when realtime price differs too much from latest daily close")
    void price_quality_low_when_current_price_gap_is_high() throws Exception {
        String prompt = builder.buildUserPrompt(
                "005930", "Samsung Electronics",
                sampleDailyPrices(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), null, List.of(),
                null,
                new BigDecimal("120000"),
                null, null
        );

        JsonNode quality = parseInputJson(prompt).path("priceDataQuality");

        assertThat(quality.path("currentPriceDailyCloseGapRate").decimalValue())
                .isGreaterThan(new BigDecimal("5.0"));
        assertThat(quality.path("quality").asText()).isEqualTo("LOW");
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

        assertThat(stock.path("latestDailyTradeDate").asText()).isEqualTo("2026-06-05");
        assertThat(stock.path("latestDailyClosePrice").decimalValue()).isEqualByComparingTo("335000.00");
        assertThat(quality.path("excludedLatestDailyPrice").asBoolean()).isTrue();
        assertThat(quality.path("currentPriceDailyCloseGapRate").decimalValue())
                .isLessThan(new BigDecimal("5.0"));
        assertThat(quality.path("quality").asText()).isEqualTo("HIGH");
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
