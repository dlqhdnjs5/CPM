package com.bowon.cpm.ai.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.ai.domain.*;
import com.bowon.cpm.ai.mapper.*;
import com.bowon.cpm.ai.parser.AiDecisionParser;
import com.bowon.cpm.ai.prompt.AiDecisionPromptBuilder;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import com.bowon.cpm.feedback.mapper.AiFeedbackMapper;
import com.bowon.cpm.feedback.mapper.AiPeriodicSummaryMapper;
import com.bowon.cpm.common.domain.ExternalApiCallLog;
import com.bowon.cpm.common.mapper.ExternalApiCallLogMapper;
import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.dart.mapper.DartDisclosureMapper;
import com.bowon.cpm.dart.mapper.DartMajorEventMapper;
import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.mapper.StockIndicatorDailyMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.news.mapper.StockNewsMapper;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * AI 매매 판단 생성 서비스 (Plan 13 리팩토링)
 *
 * 트랜잭션 분리:
 *  - 외부 API 호출(OpenAI/KIS)은 트랜잭션 외부에서 수행
 *  - DB 저장만 REQUIRES_NEW 트랜잭션으로 격리
 *
 * 데이터 수집 (P2):
 *  - 일봉 60일, 뉴스 7일, 공시 3개월, 주요이벤트 3개월
 *  - 기술적 지표 최신 1건 (stock_indicator_daily)
 *  - 실시간 현재가 (KIS)
 *
 * 응답 처리 (P3):
 *  - analysis 객체 + factors 배열 활용
 *  - factor 다건 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDecisionService {

    private final OpenAiDecisionClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final AiDecisionPromptBuilder promptBuilder;
    private final AiDecisionParser parser;

    private final BrokerClient brokerClient;
    private final TradingProperties tradingProperties;
    private final PaperPortfolioService paperPortfolioService;
    private final KisProperties kisProperties;
    private final StockService stockService;
    private final StockPriceDailyMapper stockPriceDailyMapper;
    private final StockIndicatorDailyMapper stockIndicatorDailyMapper;
    private final StockNewsMapper stockNewsMapper;
    private final DartDisclosureMapper dartDisclosureMapper;
    private final DartMajorEventMapper dartMajorEventMapper;
    private final DartFinancialService dartFinancialService;

    // 조회용 (insert/update는 persistService 위임)
    private final AiDecisionMapper decisionMapper;
    private final ExternalApiCallLogMapper externalApiCallLogMapper;
    private final AiFeedbackMapper aiFeedbackMapper;
    private final AiPeriodicSummaryMapper aiPeriodicSummaryMapper;

    /** Plan 13 트랜잭션 분리: REQUIRES_NEW가 self-invocation으로 무효화되지 않도록 별도 빈으로 분리. */
    private final AiDecisionPersistService persistService;

    private static final BigDecimal HIGH_CONFIDENCE_THRESHOLD = new BigDecimal("0.8");

    /** AI 프롬프트에 포함할 피드백 타입 (DAILY 제외 — Plan 14 Phase 1) */
    private static final List<String> FEEDBACK_TYPES_FOR_PROMPT =
            List.of("WEEKLY", "MONTHLY", "HOLDING_END");

    /**
     * AI 매매 판단 생성 (외부 진입점, 트랜잭션 없음)
     */
    public AiDecision generateDecision(String stockCode) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            // 1. 종목명 조회
            String stockName = stockService.findByStockCode(stockCode)
                    .map(s -> s.getStockName())
                    .orElse(stockCode);

            // 2. 데이터 수집 (트랜잭션 외부)
            PromptInputData input = collectPromptInput(stockCode);

            // 3. 프롬프트 생성
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(
                    stockCode, stockName,
                    input.dailyPrices, input.newsList, input.disclosures,
                    input.totalAsset, input.availableCash,
                    input.recentFeedbacks, input.financialSummary, input.majorEvents,
                    input.indicator, input.realtimeQuote,
                    input.weeklySummary, input.monthlySummary
            );

            // 4. prompt log 저장
            AiPromptLog promptLog = AiPromptLog.builder()
                    .stockCode(stockCode)
                    .promptType("DECISION")
                    .modelName(openAiProperties.modelDecision())
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .build();
            persistService.insertPromptLog(promptLog);

            // 5. OpenAI 호출 (트랜잭션 외부)
            OpenAiResponse aiResponse = openAiClient.createDecision(systemPrompt, userPrompt);
            String responseText = aiResponse.extractText();

            // 6. raw_response 저장
            AiDecisionRawResponse rawResponse = AiDecisionRawResponse.builder()
                    .promptLogId(promptLog.getId())
                    .stockCode(stockCode)
                    .modelName(openAiProperties.modelDecision())
                    .responseText(responseText != null ? responseText : "")
                    .promptTokens(aiResponse.usage() != null ? aiResponse.usage().inputTokens() : null)
                    .completionTokens(aiResponse.usage() != null ? aiResponse.usage().outputTokens() : null)
                    .totalTokens(aiResponse.usage() != null ? aiResponse.usage().totalTokens() : null)
                    .isParsed(false)
                    .build();
            persistService.insertRawResponse(rawResponse);

            if (responseText == null || responseText.isBlank()) {
                log.warn("[AI] 응답 텍스트 없음: stockCode={}", stockCode);
                return null;
            }

            // 7. JSON 파싱
            AiTradeDecisionJson parsed;
            try {
                parsed = parser.parse(responseText);
                persistService.updateParsedSuccess(rawResponse.getId());
            } catch (Exception e) {
                log.error("[AI] 파싱 실패: stockCode={}, error={}", stockCode, e.getMessage());
                persistService.updateParseError(rawResponse.getId(), e.getMessage());
                return null;
            }

            // 8. ai_decision 저장 (P1-3: stockCode/stockName 강제 주입)
            AiDecision decision = AiDecision.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .decision(parsed.decision())
                    .confidence(parsed.confidence())
                    .currentPrice(parsed.currentPrice())
                    .targetPrice(parsed.targetPrice())
                    .stopLossPrice(parsed.stopLossPrice())
                    .expectedReturnRate(parsed.expectedReturnRate())
                    .expectedLossRate(parsed.expectedLossRate())
                    .riskRewardRatio(parsed.riskRewardRatio())
                    .recommendedPortfolioWeight(parsed.recommendedPortfolioWeight())
                    .expectedHoldingDays(parsed.expectedHoldingDays())
                    .riskLevel(parsed.riskLevel())
                    .reason(parsed.reason())
                    .rawResponseId(rawResponse.getId())
                    .decisionStatus("CREATED")
                    .build();
            persistService.insertDecision(decision);

            // 9. 고신뢰 BUY 재검토
            if ("BUY".equals(parsed.decision())
                    && parsed.confidence() != null
                    && parsed.confidence().compareTo(HIGH_CONFIDENCE_THRESHOLD) >= 0) {

                log.info("[AI] 고신뢰 BUY 재검토 시작: stockCode={}, confidence={}, model={}",
                        stockCode, parsed.confidence(), openAiProperties.modelReview());
                try {
                    OpenAiResponse reviewResponse = openAiClient.createDecision(
                            systemPrompt, userPrompt, openAiProperties.modelReview());
                    String reviewText = reviewResponse.extractText();
                    if (reviewText != null && !reviewText.isBlank()) {
                        AiTradeDecisionJson reviewParsed = parser.parse(reviewText);
                        if (!"BUY".equals(reviewParsed.decision())) {
                            persistService.updateDecisionStatus(decision.getId(), "HOLD_BY_REVIEW");
                            log.warn("[AI] 재검토 결과 불일치: 1차={}, 재검토={} → HOLD_BY_REVIEW",
                                    parsed.decision(), reviewParsed.decision());
                        } else {
                            log.info("[AI] 재검토 일치: BUY 확인됨 (confidence={})", reviewParsed.confidence());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[AI] 재검토 실패 (원래 판단 유지): {}", e.getMessage());
                }
            }

            // 10. ai_decision_factor 저장 (P3-3: 다건)
            List<AiDecisionFactor> factors = buildFactors(decision.getId(), parsed);
            if (!factors.isEmpty()) {
                persistService.insertFactors(factors);
            }

            success = true;
            log.info("[AI] 판단 생성 완료: stockCode={}, decision={}, confidence={}, factors={}",
                    stockCode, parsed.decision(), parsed.confidence(), factors.size());

            return decision;

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[AI] 판단 생성 실패: stockCode={}, error={}", stockCode, errorMessage);
            throw e;
        } finally {
            saveApiLog(stockCode, success, errorMessage, System.currentTimeMillis() - start);
        }
    }

    // ===========================================================================
    // 데이터 수집 (트랜잭션 외부)
    // ===========================================================================

    private PromptInputData collectPromptInput(String stockCode) {
        PromptInputData d = new PromptInputData();

        // P2-1: 일봉 60일
        d.dailyPrices = safeCall(() -> stockPriceDailyMapper.findByStockCodeAndDateRange(
                stockCode, LocalDate.now().minusDays(60), LocalDate.now()),
                Collections.emptyList(), "일봉 조회");

        // P2-2: 뉴스 7일
        d.newsList = safeCall(() -> stockNewsMapper.findByStockCodeAndPublishedAfter(
                stockCode, LocalDateTime.now().minusDays(7), 20),
                Collections.emptyList(), "뉴스 조회");

        // 공시 3개월 (기존)
        d.disclosures = safeCall(() -> dartDisclosureMapper.findByStockCodeAndDateRange(
                stockCode, LocalDate.now().minusMonths(3), LocalDate.now()),
                Collections.emptyList(), "공시 조회");

        // P2-3: 주요이벤트 3개월
        d.majorEvents = safeCall(() -> dartMajorEventMapper.findByStockCodeAndDateAfter(
                stockCode, LocalDate.now().minusMonths(3), 10),
                Collections.emptyList(), "주요 이벤트 조회");

        // P2-4: 기술적 지표 최신 1건
        d.indicator = safeCall(
                () -> stockIndicatorDailyMapper.findLatestByStockCode(stockCode).orElse(null),
                null, "기술적 지표 조회");

        // 재무 요약
        d.financialSummary = safeCall(() -> dartFinancialService.summarize(stockCode),
                null, "재무 요약");

        // 피드백 (Plan 14 Phase 1: DAILY 제외)
        d.recentFeedbacks = safeCall(() -> aiFeedbackMapper.findRecentByStockCodeAndTypes(
                stockCode, FEEDBACK_TYPES_FOR_PROMPT, 3),
                Collections.emptyList(), "피드백 조회");

        // Plan 14 Phase 5: 최신 WEEKLY / MONTHLY 요약
        d.weeklySummary = safeCall(
                () -> aiPeriodicSummaryMapper.findLatestBySummaryType("WEEKLY").orElse(null),
                null, "WEEKLY 요약 조회");
        d.monthlySummary = safeCall(
                () -> aiPeriodicSummaryMapper.findLatestBySummaryType("MONTHLY").orElse(null),
                null, "MONTHLY 요약 조회");

        // 계좌 잔고
        try {
            AccountBalanceResult balance = loadPromptAccountBalance();
            if (balance != null) {
                d.totalAsset = balance.getTotalAssetAmount();
                d.availableCash = balance.getAvailableCash();
            }
        } catch (Exception e) {
            log.warn("[AI] 계좌 잔고 조회 실패: {}", e.getMessage());
        }

        // P2-5: 실시간 현재가 (KIS)
        try {
            StockQuoteResult quote = brokerClient.getCurrentPrice(stockCode);
            if (quote != null && quote.getCurrentPrice() != null) {
                d.realtimeQuote = quote.getCurrentPrice();
            }
        } catch (Exception e) {
            log.warn("[AI] 실시간 현재가 조회 실패 (일봉 종가 사용): {}", e.getMessage());
        }

        return d;
    }

    // ===========================================================================
    // 트랜잭션 분리: DB 저장은 AiDecisionPersistService(REQUIRES_NEW)에 위임
    // ===========================================================================

    // (이전에 여기 있던 insertPromptLog / insertRawResponse / updateParseError /
    //  insertDecision / updateDecisionStatus / insertFactors 메서드는
    //  AiDecisionPersistService 로 이동되었음. self-invocation으로 인한
    //  @Transactional 무효화 문제 해결.)

    // ===========================================================================
    // 조회
    // ===========================================================================

    @Transactional(readOnly = true)
    public List<AiDecision> getDecisions(String stockCode, int limit) {
        return decisionMapper.findByStockCode(stockCode, limit);
    }

    @Transactional(readOnly = true)
    public Optional<AiDecision> getDecision(Long id) {
        return decisionMapper.findById(id);
    }

    // ===========================================================================
    // 유틸
    // ===========================================================================

    /** P3-3: AI 응답의 factors 우선, 없으면 reason 기반 fallback */
    private List<AiDecisionFactor> buildFactors(Long decisionId, AiTradeDecisionJson parsed) {
        List<AiDecisionFactor> result = new ArrayList<>();

        if (parsed.factors() != null && !parsed.factors().isEmpty()) {
            for (AiTradeDecisionJson.FactorJson f : parsed.factors()) {
                if (f == null || f.type() == null || f.direction() == null) continue;
                result.add(AiDecisionFactor.builder()
                        .aiDecisionId(decisionId)
                        .factorType(f.type())
                        .factorDirection(f.direction())
                        .factorScore(f.score())
                        .factorSummary(f.summary() != null ? f.summary() : "")
                        .build());
            }
            if (!result.isEmpty()) return result;
        }

        if (parsed.reason() != null && !parsed.reason().isBlank()) {
            String direction = switch (parsed.decision() != null ? parsed.decision() : "HOLD") {
                case "BUY" -> "POSITIVE";
                case "SELL" -> "NEGATIVE";
                default -> "NEUTRAL";
            };
            result.add(AiDecisionFactor.builder()
                    .aiDecisionId(decisionId)
                    .factorType("TECHNICAL")
                    .factorDirection(direction)
                    .factorScore(parsed.confidence())
                    .factorSummary(parsed.reason())
                    .build());
        }
        return result;
    }

    private void saveApiLog(String stockCode, boolean success, String errorMessage, long elapsedMs) {
        try {
            externalApiCallLogMapper.insert(ExternalApiCallLog.builder()
                    .provider("OPENAI")
                    .apiName("AI판단생성")
                    .httpMethod("POST")
                    .requestUrl("/v1/responses?stockCode=" + stockCode)
                    .success(success)
                    .errorMessage(errorMessage)
                    .elapsedMs(elapsedMs)
                    .calledAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[ExternalApiLog] 로그 저장 실패: {}", e.getMessage());
        }
    }

    private AccountBalanceResult loadPromptAccountBalance() {
        if (tradingProperties.isPaperMode()) {
            return paperPortfolioService.findLatestAccountBalance(kisProperties.accountNo())
                    .map(balance -> AccountBalanceResult.builder()
                            .accountNo(balance.getAccountNo())
                            .cashBalance(balance.getCashBalance())
                            .availableCash(balance.getAvailableCash())
                            .totalAssetAmount(balance.getTotalAssetAmount())
                            .totalEvaluationAmount(balance.getTotalEvaluationAmount())
                            .totalProfitLossAmount(balance.getTotalProfitLossAmount())
                            .totalProfitLossRate(balance.getTotalProfitLossRate())
                            .build())
                    .orElse(null);
        }
        return brokerClient.getAccountBalance();
    }

    private <T> T safeCall(java.util.function.Supplier<T> supplier, T fallback, String label) {
        try {
            T r = supplier.get();
            return r != null ? r : fallback;
        } catch (Exception e) {
            log.warn("[AI] {} 실패: {}", label, e.getMessage());
            return fallback;
        }
    }

    private static class PromptInputData {
        List<StockPriceDaily> dailyPrices = Collections.emptyList();
        List<StockNews> newsList = Collections.emptyList();
        List<DartDisclosure> disclosures = Collections.emptyList();
        List<DartMajorEvent> majorEvents = Collections.emptyList();
        List<AiFeedback> recentFeedbacks = Collections.emptyList();
        StockIndicatorDaily indicator;
        String financialSummary;
        BigDecimal totalAsset;
        BigDecimal availableCash;
        BigDecimal realtimeQuote;
        AiPeriodicSummary weeklySummary;
        AiPeriodicSummary monthlySummary;
    }
}
