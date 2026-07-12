package com.bowon.cpm.feedback.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import com.bowon.cpm.feedback.mapper.AiFeedbackMapper;
import com.bowon.cpm.feedback.mapper.AiPeriodicSummaryMapper;
import com.bowon.cpm.feedback.mapper.PortfolioProfitLossMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.portfolio.mapper.AccountBalanceMapper;
import com.bowon.cpm.support.fixture.AiDecisionFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plan 16: FeedbackService 단위 테스트 (Case 1~10).
 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock AiDecisionMapper aiDecisionMapper;
    @Mock AiFeedbackMapper aiFeedbackMapper;
    @Mock PortfolioProfitLossMapper portfolioProfitLossMapper;
    @Mock AccountBalanceMapper accountBalanceMapper;
    @Mock BrokerClient brokerClient;
    @Mock KisProperties kisProperties;
    @Mock StockPriceDailyMapper stockPriceDailyMapper;
    @Mock AiPeriodicSummaryMapper aiPeriodicSummaryMapper;
    @Mock OpenAiDecisionClient openAiDecisionClient;
    @Mock OpenAiProperties openAiProperties;

    ObjectMapper objectMapper = new ObjectMapper();

    FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(
                aiDecisionMapper,
                aiFeedbackMapper,
                portfolioProfitLossMapper,
                accountBalanceMapper,
                brokerClient,
                kisProperties,
                stockPriceDailyMapper,
                aiPeriodicSummaryMapper,
                openAiDecisionClient,
                openAiProperties,
                objectMapper
        );
    }

    // ===== evaluateDecision: DAILY =====

    @Test
    @DisplayName("Case 1: DAILY BUY 목표가 도달 → success=true, returnRate≈+15%")
    void evaluateDecision_DAILY_BUY_목표가도달_성공() {
        // Given
        AiDecision d = AiDecisionFixture.buy(1L,
                new BigDecimal("10000"),
                new BigDecimal("11000"),
                new BigDecimal("9000"));
        when(aiDecisionMapper.findById(1L)).thenReturn(Optional.of(d));
        when(brokerClient.getCurrentPrice("TEST_001"))
                .thenReturn(StockQuoteResult.builder()
                        .stockCode("TEST_001")
                        .currentPrice(new BigDecimal("11500"))
                        .build());

        // When
        AiFeedback fb = feedbackService.evaluateDecision(1L, "DAILY");

        // Then
        assertThat(fb.getTargetReached()).isTrue();
        assertThat(fb.getStopLossReached()).isFalse();
        assertThat(fb.getSuccess()).isTrue();
        assertThat(fb.getReturnRate()).isEqualByComparingTo(new BigDecimal("15.0000"));
        verify(aiFeedbackMapper, times(1)).insertIgnore(any(AiFeedback.class));
    }

    @Test
    @DisplayName("Case 2: DAILY BUY 손절가 도달 → success=false, returnRate≈-12%")
    void evaluateDecision_DAILY_BUY_손절가도달_실패() {
        AiDecision d = AiDecisionFixture.buy(2L,
                new BigDecimal("10000"),
                new BigDecimal("11000"),
                new BigDecimal("9000"));
        when(aiDecisionMapper.findById(2L)).thenReturn(Optional.of(d));
        when(brokerClient.getCurrentPrice("TEST_001"))
                .thenReturn(StockQuoteResult.builder()
                        .currentPrice(new BigDecimal("8800"))
                        .build());

        AiFeedback fb = feedbackService.evaluateDecision(2L, "DAILY");

        assertThat(fb.getStopLossReached()).isTrue();
        assertThat(fb.getTargetReached()).isFalse();
        assertThat(fb.getSuccess()).isFalse();
        assertThat(fb.getReturnRate()).isEqualByComparingTo(new BigDecimal("-12.0000"));
    }

    @Test
    @DisplayName("Case 3: DAILY SELL 역방향 성공 (현재가가 목표가 이하)")
    void evaluateDecision_DAILY_SELL_역방향성공() {
        AiDecision d = AiDecisionFixture.sell(3L,
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                new BigDecimal("11000"));
        when(aiDecisionMapper.findById(3L)).thenReturn(Optional.of(d));
        when(brokerClient.getCurrentPrice("TEST_001"))
                .thenReturn(StockQuoteResult.builder()
                        .currentPrice(new BigDecimal("8800"))
                        .build());

        AiFeedback fb = feedbackService.evaluateDecision(3L, "DAILY");

        assertThat(fb.getTargetReached()).isTrue();
        assertThat(fb.getStopLossReached()).isFalse();
        assertThat(fb.getSuccess()).isTrue();
    }

    @Test
    @DisplayName("Case 4: 현재가 조회 실패 → basePrice로 폴백, returnRate=0")
    void evaluateDecision_currentPrice조회실패_basePrice대체() {
        AiDecision d = AiDecisionFixture.buy(4L,
                new BigDecimal("10000"),
                new BigDecimal("11000"),
                new BigDecimal("9000"));
        when(aiDecisionMapper.findById(4L)).thenReturn(Optional.of(d));
        when(brokerClient.getCurrentPrice(anyString()))
                .thenThrow(new RuntimeException("KIS 장애"));

        AiFeedback fb = feedbackService.evaluateDecision(4L, "DAILY");

        assertThat(fb.getEvaluatedPrice()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(fb.getReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
        // basePrice == target/stop 미도달 → success=false
        assertThat(fb.getTargetReached()).isFalse();
        assertThat(fb.getStopLossReached()).isFalse();
        assertThat(fb.getSuccess()).isFalse();
    }

    @Test
    @DisplayName("Case 5: 중복 저장 시 insertIgnore 호출 (UK가 차단) — 항상 1회 호출")
    void evaluateDecision_중복저장_INSERT_IGNORE_검증() {
        AiDecision d = AiDecisionFixture.buy(5L,
                new BigDecimal("10000"),
                new BigDecimal("11000"),
                new BigDecimal("9000"));
        when(aiDecisionMapper.findById(5L)).thenReturn(Optional.of(d));
        when(brokerClient.getCurrentPrice(anyString()))
                .thenReturn(StockQuoteResult.builder().currentPrice(new BigDecimal("10500")).build());

        feedbackService.evaluateDecision(5L, "DAILY");
        feedbackService.evaluateDecision(5L, "DAILY");

        // 서비스는 매번 insertIgnore 호출 (실제 중복 차단은 DB UK 책임)
        verify(aiFeedbackMapper, times(2)).insertIgnore(any(AiFeedback.class));
    }

    // ===== evaluateHoldingDayEnd =====

    @Test
    @DisplayName("Case 6: HoldingDay 만기, 기간 중 최고가가 목표가 도달 → success=true")
    void evaluateHoldingDayEnd_기간중최고가_목표가도달_성공() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(21);
        AiDecision d = AiDecisionFixture.buyMatured(6L, 20, createdAt);
        // currentPrice=10000, target=11000, stopLoss=9000 (defaults)
        when(aiDecisionMapper.findById(6L)).thenReturn(Optional.of(d));
        when(stockPriceDailyMapper.findHighLowInRange(eq("TEST_001"), any(), any()))
                .thenReturn(Map.of(
                        "high", new BigDecimal("11500"),
                        "low", new BigDecimal("9500"),
                        "last_close", new BigDecimal("10500")
                ));

        AiFeedback fb = feedbackService.evaluateHoldingDayEnd(6L);

        assertThat(fb.getEvaluationType()).isEqualTo("HOLDING_END");
        assertThat(fb.getHighestPrice()).isEqualByComparingTo(new BigDecimal("11500"));
        assertThat(fb.getLowestPrice()).isEqualByComparingTo(new BigDecimal("9500"));
        assertThat(fb.getEvaluatedPrice()).isEqualByComparingTo(new BigDecimal("10500"));
        assertThat(fb.getTargetReached()).isTrue();
        assertThat(fb.getStopLossReached()).isFalse();
        assertThat(fb.getSuccess()).isTrue();
        assertThat(fb.getReturnRate()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("Case 7: HoldingDay 만기, 기간 중 최저가가 손절가 도달 → success=false")
    void evaluateHoldingDayEnd_기간중최저가_손절도달_실패() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(21);
        AiDecision d = AiDecisionFixture.buyMatured(7L, 20, createdAt);
        when(aiDecisionMapper.findById(7L)).thenReturn(Optional.of(d));
        when(stockPriceDailyMapper.findHighLowInRange(eq("TEST_001"), any(), any()))
                .thenReturn(Map.of(
                        "high", new BigDecimal("10800"),
                        "low", new BigDecimal("8700"),
                        "last_close", new BigDecimal("9200")
                ));

        AiFeedback fb = feedbackService.evaluateHoldingDayEnd(7L);

        assertThat(fb.getTargetReached()).isFalse();
        assertThat(fb.getStopLossReached()).isTrue();
        assertThat(fb.getSuccess()).isFalse();
    }

    @Test
    @DisplayName("Case 8: HoldingDay 미만기 (만기일이 미래) → endDate=오늘로 강제 평가, 결과 저장")
    void evaluateHoldingDayEnd_미만기_endDate_today로_강제() {
        // FeedbackService는 미만기여도 endDate=today로 강제 평가하고 저장한다.
        // 스케줄러가 findHoldingDayMaturedDecisions로 미만기 제외 책임을 짐 → 서비스는 평가만.
        LocalDateTime createdAt = LocalDateTime.now().minusDays(5);
        AiDecision d = AiDecisionFixture.buyMatured(8L, 20, createdAt); // 만기 -15일 후
        when(aiDecisionMapper.findById(8L)).thenReturn(Optional.of(d));
        when(stockPriceDailyMapper.findHighLowInRange(eq("TEST_001"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Map.of(
                        "high", new BigDecimal("10200"),
                        "low", new BigDecimal("9800"),
                        "last_close", new BigDecimal("10100")
                ));

        AiFeedback fb = feedbackService.evaluateHoldingDayEnd(8L);

        assertThat(fb).isNotNull();
        verify(aiFeedbackMapper, times(1)).insertIgnore(any(AiFeedback.class));
        // endDate가 오늘로 잘려도 정상 동작
        ArgumentCaptor<LocalDate> endCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(stockPriceDailyMapper).findHighLowInRange(eq("TEST_001"), any(LocalDate.class), endCap.capture());
        assertThat(endCap.getValue()).isEqualTo(LocalDate.now());
    }

    // ===== evaluateWeekly / evaluateMonthly =====

    @Test
    @DisplayName("Case 9: WEEKLY 집계 — aggregate + decisionDistribution + LLM 요약 저장")
    void evaluateWeekly_집계통계_정확성() {
        LocalDate from = LocalDate.of(2026, 5, 25);
        LocalDate to = LocalDate.of(2026, 5, 29);

        when(aiFeedbackMapper.aggregateStatsBetween(from, to)).thenReturn(Map.of(
                "total", 5,
                "success_cnt", 3,
                "target_cnt", 2,
                "stop_loss_cnt", 2,
                "avg_return", new BigDecimal("1.60"),
                "max_return", new BigDecimal("8.00"),
                "min_return", new BigDecimal("-4.00")
        ));

        AiDecision d1 = AiDecisionFixture.defaults().id(101L).decision("BUY").confidence(new BigDecimal("0.85")).build();
        AiDecision d2 = AiDecisionFixture.defaults().id(102L).decision("SELL").confidence(new BigDecimal("0.91")).build();
        AiDecision d3 = AiDecisionFixture.defaults().id(103L).decision("HOLD").confidence(new BigDecimal("0.72")).build();
        when(aiDecisionMapper.findDecisionsBetween(any(), any())).thenReturn(List.of(d1, d2, d3));

        when(openAiProperties.modelDecision()).thenReturn("gpt-4.1-mini");
        when(openAiDecisionClient.createTextCompletion(anyString(), anyString(), anyString()))
                .thenReturn(stubOpenAiResponse("주간 요약 텍스트"));

        AiPeriodicSummary summary = feedbackService.evaluateWeekly(from, to);

        assertThat(summary.getSummaryType()).isEqualTo("WEEKLY");
        assertThat(summary.getPeriodStart()).isEqualTo(from);
        assertThat(summary.getPeriodEnd()).isEqualTo(to);
        assertThat(summary.getLlmSummary()).isEqualTo("주간 요약 텍스트");
        assertThat(summary.getStatsJson()).contains("\"BUY\":1");
        assertThat(summary.getStatsJson()).contains("\"SELL\":1");
        assertThat(summary.getStatsJson()).contains("\"HOLD\":1");
        verify(aiPeriodicSummaryMapper, times(1)).insertIgnore(any(AiPeriodicSummary.class));
    }

    @Test
    @DisplayName("Case 10: MONTHLY 집계 — summaryType=MONTHLY로 저장")
    void evaluateMonthly_집계통계_정확성() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);

        when(aiFeedbackMapper.aggregateStatsBetween(from, to)).thenReturn(Map.of(
                "total", 20,
                "success_cnt", 12,
                "avg_return", new BigDecimal("2.30")
        ));
        when(aiDecisionMapper.findDecisionsBetween(any(), any())).thenReturn(List.of());
        when(openAiProperties.modelDecision()).thenReturn("gpt-4.1-mini");
        when(openAiDecisionClient.createTextCompletion(anyString(), anyString(), anyString()))
                .thenReturn(stubOpenAiResponse("월간 전략 개선 제안"));

        AiPeriodicSummary summary = feedbackService.evaluateMonthly(from, to);

        assertThat(summary.getSummaryType()).isEqualTo("MONTHLY");
        assertThat(summary.getLlmSummary()).isEqualTo("월간 전략 개선 제안");
        verify(aiPeriodicSummaryMapper, times(1)).insertIgnore(any(AiPeriodicSummary.class));
    }

    @Test
    @DisplayName("LLM 호출 실패 시 폴백 텍스트로 저장")
    void evaluatePeriodic_LLM실패_폴백() {
        LocalDate from = LocalDate.of(2026, 5, 25);
        LocalDate to = LocalDate.of(2026, 5, 29);
        when(aiFeedbackMapper.aggregateStatsBetween(from, to)).thenReturn(Map.of());
        when(aiDecisionMapper.findDecisionsBetween(any(), any())).thenReturn(List.of());
        when(openAiProperties.modelDecision()).thenReturn("gpt-4.1-mini");
        when(openAiDecisionClient.createTextCompletion(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("OpenAI 장애"));

        AiPeriodicSummary summary = feedbackService.evaluateWeekly(from, to);

        assertThat(summary.getLlmSummary()).contains("자동 요약 생성 실패");
        verify(aiPeriodicSummaryMapper, times(1)).insertIgnore(any(AiPeriodicSummary.class));
    }

    @Test
    @DisplayName("DAILY SELL target not reached but price declined -> success=true")
    void evaluateDecision_DAILY_SELL_directionSuccessBeforeTarget() {
        AiDecision d = AiDecisionFixture.sell(31L,
                new BigDecimal("289000"),
                new BigDecimal("260000"),
                new BigDecimal("300000"));
        when(aiDecisionMapper.findById(31L)).thenReturn(Optional.of(d));
        when(brokerClient.getCurrentPrice("TEST_001"))
                .thenReturn(StockQuoteResult.builder()
                        .currentPrice(new BigDecimal("283000"))
                        .build());

        AiFeedback fb = feedbackService.evaluateDecision(31L, "DAILY");

        assertThat(fb.getTargetReached()).isFalse();
        assertThat(fb.getStopLossReached()).isFalse();
        assertThat(fb.getSuccess()).isTrue();
        assertThat(fb.getReturnRate()).isEqualByComparingTo(new BigDecimal("2.0761"));
    }

    // ===== helpers =====

    private OpenAiResponse stubOpenAiResponse(String text) {
        OpenAiResponse.ContentItem content = new OpenAiResponse.ContentItem("output_text", text);
        OpenAiResponse.OutputItem output = new OpenAiResponse.OutputItem(
                "message", "id_1", "completed", "assistant", List.of(content));
        return new OpenAiResponse("id_resp", "completed", List.of(output), null);
    }
}


