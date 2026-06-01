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
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.mapper.AiFeedbackMapper;
import com.bowon.cpm.common.domain.ExternalApiCallLog;
import com.bowon.cpm.common.mapper.ExternalApiCallLogMapper;
import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.dart.mapper.DartDisclosureMapper;
import com.bowon.cpm.dart.mapper.DartMajorEventMapper;
import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.news.mapper.StockNewsMapper;
import com.bowon.cpm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDecisionService {

    private final OpenAiDecisionClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final AiDecisionPromptBuilder promptBuilder;
    private final AiDecisionParser parser;

    private final BrokerClient brokerClient;
    private final StockService stockService;
    private final StockPriceDailyMapper stockPriceDailyMapper;
    private final StockNewsMapper stockNewsMapper;
    private final DartDisclosureMapper dartDisclosureMapper;
    private final DartMajorEventMapper dartMajorEventMapper;
    private final DartFinancialService dartFinancialService;

    private final AiPromptLogMapper promptLogMapper;
    private final AiDecisionRawResponseMapper rawResponseMapper;
    private final AiDecisionMapper decisionMapper;
    private final AiDecisionFactorMapper decisionFactorMapper;
    private final ExternalApiCallLogMapper externalApiCallLogMapper;
    private final AiFeedbackMapper aiFeedbackMapper;

    /**
     * 고신뢰 BUY 재검토 임계값
     * confidence ≥ 0.8 이고 BUY 판단이면 model-review(gpt-4.1)로 재검토
     */
    private static final BigDecimal HIGH_CONFIDENCE_THRESHOLD = new BigDecimal("0.8");

    /**
     * AI 매매 판단 생성 전체 플로우
     *
     * 1. 현재가 조회 (KIS)
     * 2. 일봉/뉴스/공시 데이터 수집 (DB)
     * 3. 프롬프트 생성 + ai_prompt_log 저장
     * 4. OpenAI API 호출 (model-decision: gpt-4.1-mini)
     * 5. ai_decision_raw_response 저장
     * 6. JSON 파싱 → ai_decision 저장
     * 7. BUY + confidence ≥ 0.8 → model-review(gpt-4.1)로 재검토
     * 8. ai_decision_factor 저장
     *
     * @param stockCode 종목 코드
     * @return 생성된 AiDecision (파싱 실패 시 null)
     */
    @Transactional
    public AiDecision generateDecision(String stockCode) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            // 1. 종목명 조회
            String stockName = stockService.findByStockCode(stockCode)
                    .map(s -> s.getStockName())
                    .orElse(stockCode);

            // 2. 데이터 수집
            List<StockPriceDaily> dailyPrices = stockPriceDailyMapper.findByStockCodeAndDateRange(
                    stockCode, LocalDate.now().minusDays(30), LocalDate.now());
            List<StockNews> newsList = stockNewsMapper.findByStockCode(stockCode, 10);
            List<DartDisclosure> disclosures = dartDisclosureMapper.findByStockCodeAndDateRange(
                    stockCode, LocalDate.now().minusMonths(3), LocalDate.now());

            // 2-1. 계좌 잔고 조회 (트랜잭션 외부 호출이 원칙이나, AI 판단 흐름에서는 read-only 조회이므로 허용)
            BigDecimal totalAsset = null;
            BigDecimal availableCash = null;
            try {
                AccountBalanceResult balance = brokerClient.getAccountBalance();
                if (balance != null) {
                    totalAsset = balance.getTotalAssetAmount();
                    availableCash = balance.getAvailableCash();
                }
            } catch (Exception e) {
                log.warn("[AI] 계좌 잔고 조회 실패 (프롬프트에 계좌 정보 미포함): {}", e.getMessage());
            }

            // 3. 프롬프트 생성
            String systemPrompt = promptBuilder.buildSystemPrompt();

            // 3-1. 최근 피드백 조회 (최대 3건 — 과거 판단 품질 참고용)
            List<AiFeedback> recentFeedbacks;
            try {
                recentFeedbacks = aiFeedbackMapper.findRecentByStockCode(stockCode, 3);
            } catch (Exception e) {
                log.warn("[AI] 피드백 조회 실패 (프롬프트에 피드백 미포함): {}", e.getMessage());
                recentFeedbacks = java.util.Collections.emptyList();
            }

            // 3-2. 재무 요약 조회 (OpenAI or fallback 텍스트)
            String financialSummary = null;
            try {
                financialSummary = dartFinancialService.summarize(stockCode);
            } catch (Exception e) {
                log.warn("[AI] 재무 요약 조회 실패: {}", e.getMessage());
            }

            // 3-3. 주요 이벤트 조회 (최대 5건)
            List<DartMajorEvent> majorEvents;
            try {
                majorEvents = dartMajorEventMapper.findByStockCode(stockCode, 5);
            } catch (Exception e) {
                log.warn("[AI] 주요 이벤트 조회 실패: {}", e.getMessage());
                majorEvents = java.util.Collections.emptyList();
            }

            String userPrompt = promptBuilder.buildUserPrompt(
                    stockCode, stockName, dailyPrices, newsList, disclosures,
                    totalAsset, availableCash, recentFeedbacks, financialSummary, majorEvents);

            // 4. ai_prompt_log 저장 (API 호출 전에 먼저 저장)
            AiPromptLog promptLog = AiPromptLog.builder()
                    .stockCode(stockCode)
                    .promptType("DECISION")
                    .modelName(openAiProperties.modelDecision())
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .build();
            promptLogMapper.insert(promptLog);

            // 5. OpenAI API 호출 — 1차 판단: model-decision (gpt-4.1-mini, 비용 절감)
            OpenAiResponse aiResponse = openAiClient.createDecision(systemPrompt, userPrompt);
            String responseText = aiResponse.extractText();

            // 6. ai_decision_raw_response 저장 (파싱 전에 무조건 저장)
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
            rawResponseMapper.insert(rawResponse);

            if (responseText == null || responseText.isBlank()) {
                log.warn("[AI] 응답 텍스트 없음: stockCode={}", stockCode);
                return null;
            }

            // 7. JSON 파싱
            AiTradeDecisionJson parsed;
            try {
                parsed = parser.parse(responseText);
            } catch (Exception e) {
                // 파싱 실패 → raw_response에 parse_error 기록 후 종료
                log.error("[AI] 파싱 실패: stockCode={}, error={}", stockCode, e.getMessage());
                updateParseError(rawResponse.getId(), e.getMessage());
                return null;
            }

            // 8. ai_decision 저장
            AiDecision decision = AiDecision.builder()
                    .stockCode(parsed.stockCode())
                    .stockName(parsed.stockName())
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
            decisionMapper.insert(decision);

            // 9. BUY + confidence ≥ 0.8 → model-review(gpt-4.1)로 재검토
            // 1차 판단(gpt-4.1-mini)이 고신뢰 BUY라면, 비싼 모델로 한 번 더 확인
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
                        // 재검토에서 HOLD/SELL이 나오면 원래 판단 상태를 HOLD로 변경
                        if (!"BUY".equals(reviewParsed.decision())) {
                            decisionMapper.updateDecisionStatus(decision.getId(), "HOLD_BY_REVIEW");
                            log.warn("[AI] 재검토 결과 불일치: 1차={}, 재검토={} → HOLD_BY_REVIEW",
                                    parsed.decision(), reviewParsed.decision());
                        } else {
                            log.info("[AI] 재검토 일치: BUY 확인됨 (confidence={})", reviewParsed.confidence());
                        }
                    }
                } catch (Exception e) {
                    // 재검토 실패 시 원래 판단 유지 (graceful)
                    log.warn("[AI] 재검토 실패 (원래 판단 유지): {}", e.getMessage());
                }
            }

            // 10. ai_decision_factor 저장 (reason 기반 NEUTRAL 팩터 1건)
            List<AiDecisionFactor> factors = buildFactors(decision.getId(), parsed);
            if (!factors.isEmpty()) {
                decisionFactorMapper.insertBatch(factors);
            }

            success = true;
            log.info("[AI] 판단 생성 완료: stockCode={}, decision={}, confidence={}",
                    stockCode, parsed.decision(), parsed.confidence());

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

    /**
     * AI 판단 목록 조회 (최신순)
     */
    @Transactional(readOnly = true)
    public List<AiDecision> getDecisions(String stockCode, int limit) {
        return decisionMapper.findByStockCode(stockCode, limit);
    }

    /**
     * AI 판단 단건 조회
     */
    @Transactional(readOnly = true)
    public Optional<AiDecision> getDecision(Long id) {
        return decisionMapper.findById(id);
    }

    /**
     * 판단 근거 팩터 생성
     * reason 텍스트를 기반으로 NEUTRAL 팩터 1건 저장
     * (추후 AI 응답 확장 시 여러 팩터로 분리 가능)
     */
    private List<AiDecisionFactor> buildFactors(Long decisionId, AiTradeDecisionJson parsed) {
        List<AiDecisionFactor> factors = new ArrayList<>();
        if (parsed.reason() != null && !parsed.reason().isBlank()) {
            String direction = switch (parsed.decision()) {
                case "BUY" -> "POSITIVE";
                case "SELL" -> "NEGATIVE";
                default -> "NEUTRAL";
            };
            factors.add(AiDecisionFactor.builder()
                    .aiDecisionId(decisionId)
                    .factorType("TECHNICAL")
                    .factorDirection(direction)
                    .factorScore(parsed.confidence())
                    .factorSummary(parsed.reason())
                    .build());
        }
        return factors;
    }

    private void updateParseError(Long rawResponseId, String parseError) {
        // 간단히 로그만 남김 (별도 UPDATE 쿼리 불필요)
        log.warn("[AI] raw_response_id={} parseError={}", rawResponseId, parseError);
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
}

