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
import com.bowon.cpm.feedback.domain.PortfolioProfitLoss;
import com.bowon.cpm.feedback.mapper.AiFeedbackMapper;
import com.bowon.cpm.feedback.mapper.AiPeriodicSummaryMapper;
import com.bowon.cpm.feedback.mapper.PortfolioProfitLossMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.portfolio.domain.AccountBalance;
import com.bowon.cpm.portfolio.mapper.AccountBalanceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 피드백 서비스
 *
 * 1. AI 판단 단건 평가 → ai_feedback 저장
 * 2. 일간 포트폴리오 수익률 → portfolio_profit_loss 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final AiDecisionMapper aiDecisionMapper;
    private final AiFeedbackMapper aiFeedbackMapper;
    private final PortfolioProfitLossMapper portfolioProfitLossMapper;
    private final AccountBalanceMapper accountBalanceMapper;
    private final BrokerClient brokerClient;
    private final KisProperties kisProperties;

    private final StockPriceDailyMapper stockPriceDailyMapper;
    private final AiPeriodicSummaryMapper aiPeriodicSummaryMapper;
    private final OpenAiDecisionClient openAiDecisionClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    /**
     * AI 판단 단건 피드백 평가
     *
     * @param aiDecisionId  평가할 AI 판단 ID
     * @param evaluationType DAILY / WEEKLY / MONTHLY
     * @return 저장된 AiFeedback
     */
    @Transactional
    public AiFeedback evaluateDecision(Long aiDecisionId, String evaluationType) {
        // 1. AI 판단 조회
        AiDecision decision = aiDecisionMapper.findById(aiDecisionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 판단 없음: id=" + aiDecisionId));

        // 2. 현재가 조회 (KIS API)
        BigDecimal currentPrice;
        try {
            StockQuoteResult quote = brokerClient.getCurrentPrice(decision.getStockCode());
            currentPrice = quote.getCurrentPrice();
        } catch (Exception e) {
            log.warn("[Feedback] 현재가 조회 실패, basePrice 사용: {}", e.getMessage());
            currentPrice = decision.getCurrentPrice(); // 조회 실패 시 판단 당시가로 대체
        }

        BigDecimal basePrice = decision.getCurrentPrice();

        // 3. 수익률 계산
        BigDecimal returnRate = calculateDecisionReturnRate(decision, basePrice, currentPrice);

        // 4. 목표가/손절가 도달 여부 (BUY 기준)
        Boolean targetReached = null;
        Boolean stopLossReached = null;

        if ("BUY".equals(decision.getDecision()) && currentPrice != null) {
            if (decision.getTargetPrice() != null) {
                targetReached = currentPrice.compareTo(decision.getTargetPrice()) >= 0;
            }
            if (decision.getStopLossPrice() != null) {
                stopLossReached = currentPrice.compareTo(decision.getStopLossPrice()) <= 0;
            }
        } else if ("SELL".equals(decision.getDecision()) && currentPrice != null) {
            // SELL 기준: 현재가가 판단가보다 낮으면 성공
            if (decision.getTargetPrice() != null) {
                targetReached = currentPrice.compareTo(decision.getTargetPrice()) <= 0;
            }
            if (decision.getStopLossPrice() != null) {
                stopLossReached = currentPrice.compareTo(decision.getStopLossPrice()) >= 0;
            }
        }

        // 5. 판단 성공 여부 (목표가 도달 O + 손절가 도달 X)
        Boolean success = evaluateDirectionalSuccess(decision, basePrice, currentPrice, stopLossReached, targetReached);

        // 6. 피드백 요약 생성
        String feedbackSummary = buildFeedbackSummary(decision, currentPrice, returnRate, targetReached, stopLossReached, success);

        // 7. ai_feedback 저장 (UK: ai_decision_id + evaluation_type → 중복 시 IGNORE)
        AiFeedback feedback = AiFeedback.builder()
                .aiDecisionId(aiDecisionId)
                .stockCode(decision.getStockCode())
                .evaluationType(evaluationType)
                .basePrice(basePrice)
                .evaluatedPrice(currentPrice)
                .returnRate(returnRate)
                .targetReached(targetReached)
                .stopLossReached(stopLossReached)
                .success(success)
                .feedbackSummary(feedbackSummary)
                .build();

        aiFeedbackMapper.insertIgnore(feedback);

        log.info("[Feedback] 피드백 저장: aiDecisionId={}, type={}, returnRate={}%, success={}",
                aiDecisionId, evaluationType, returnRate, success);

        return feedback;
    }

    /**
     * AI 판단 ID로 피드백 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AiFeedback> getFeedbacks(Long aiDecisionId) {
        return aiFeedbackMapper.findByAiDecisionId(aiDecisionId);
    }

    /**
     * 종목코드 기준 최근 피드백 조회 (다음 AI 판단 프롬프트 포함용)
     */
    @Transactional(readOnly = true)
    public List<AiFeedback> getRecentFeedbacks(String stockCode, int limit) {
        return aiFeedbackMapper.findRecentByStockCode(stockCode, limit);
    }

    /**
     * 일간 포트폴리오 수익률 계산 및 저장
     *
     * account_balance 테이블에서 오늘/어제 총자산을 비교해 수익률 계산
     */
    @Transactional
    public PortfolioProfitLoss saveDailyProfitLoss() {
        String accountNo = kisProperties.accountNo();
        LocalDate today = LocalDate.now();

        // 오늘 최신 잔고 조회
        List<AccountBalance> balances = accountBalanceMapper.findLatestByAccountNo(accountNo);
        if (balances == null || balances.isEmpty()) {
            throw new IllegalStateException("계좌 잔고 데이터 없음: accountNo=" + accountNo);
        }

        AccountBalance todayBalance = balances.get(0);
        BigDecimal endAsset = todayBalance.getTotalAssetAmount();

        // 어제 잔고 조회 (2번째 항목 — 없으면 오늘과 동일하게 처리)
        BigDecimal startAsset = balances.size() > 1
                ? balances.get(1).getTotalAssetAmount()
                : endAsset;

        // 수익률 계산
        BigDecimal returnRate = null;
        if (startAsset != null && startAsset.compareTo(BigDecimal.ZERO) > 0 && endAsset != null) {
            returnRate = endAsset.subtract(startAsset)
                    .divide(startAsset, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        // 미실현 손익 = 총자산 - 예수금
        BigDecimal unrealizedPL = null;
        if (endAsset != null && todayBalance.getCashBalance() != null) {
            unrealizedPL = endAsset.subtract(todayBalance.getCashBalance());
        }

        PortfolioProfitLoss profitLoss = PortfolioProfitLoss.builder()
                .accountNo(accountNo)
                .stockCode(null) // 전체 포트폴리오 집계
                .baseDate(today)
                .evaluationType("DAILY")
                .startAssetAmount(startAsset)
                .endAssetAmount(endAsset)
                .unrealizedProfitLoss(unrealizedPL)
                .returnRate(returnRate)
                .build();

        portfolioProfitLossMapper.insertIgnore(profitLoss);

        log.info("[Feedback] 일간 수익률 저장: accountNo={}, date={}, returnRate={}%, endAsset={}",
                accountNo, today, returnRate, endAsset);

        return profitLoss;
    }

    /**
     * 피드백 요약 텍스트 생성
     */
    private String buildFeedbackSummary(
            AiDecision decision,
            BigDecimal currentPrice,
            BigDecimal returnRate,
            Boolean targetReached,
            Boolean stopLossReached,
            Boolean success
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s 판단 결과: ", decision.getDecision(), decision.getStockCode()));

        if (returnRate != null) {
            sb.append(String.format("수익률 %.2f%%, ", returnRate));
        }
        if (Boolean.TRUE.equals(targetReached)) {
            sb.append("목표가 도달 O, ");
        } else if (Boolean.FALSE.equals(targetReached)) {
            sb.append("목표가 미도달, ");
        }
        if (Boolean.TRUE.equals(stopLossReached)) {
            sb.append("손절가 도달 O, ");
        }
        if (Boolean.TRUE.equals(success)) {
            sb.append("→ 성공");
        } else if (Boolean.FALSE.equals(success)) {
            sb.append("→ 실패");
        } else {
            sb.append("→ 판단 중");
        }

        sb.append(String.format(" (판단가: %.0f → 현재가: %.0f)", decision.getCurrentPrice(), currentPrice));
        return sb.toString();
    }

    // =========================================================================
    // Plan 14 Phase 2: HoldingDay 피드백
    // =========================================================================

    /**
     * AI가 제시한 expected_holding_days 만기 시점 평가.
     * - 기간 중 최고가/최저가로 target_reached/stop_loss_reached 판정
     * - evaluation_type='HOLDING_END' 로 ai_feedback 저장
     * - 이미 저장된 경우 INSERT IGNORE 로 무시
     */
    @Transactional
    public AiFeedback evaluateHoldingDayEnd(Long aiDecisionId) {
        AiDecision decision = aiDecisionMapper.findById(aiDecisionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 판단 없음: id=" + aiDecisionId));

        if (decision.getExpectedHoldingDays() == null) {
            throw new IllegalArgumentException("expected_holding_days 없음: id=" + aiDecisionId);
        }

        LocalDate startDate = decision.getCreatedAt().toLocalDate();
        LocalDate endDate = startDate.plusDays(decision.getExpectedHoldingDays());
        LocalDate today = LocalDate.now();
        if (endDate.isAfter(today)) {
            // 아직 만기 전 — 강제 호출시 오늘까지로 계산
            endDate = today;
        }

        BigDecimal basePrice = decision.getCurrentPrice();
        BigDecimal highest = null;
        BigDecimal lowest = null;
        BigDecimal lastClose = null;
        try {
            Map<String, Object> hl = stockPriceDailyMapper.findHighLowInRange(
                    decision.getStockCode(), startDate, endDate);
            if (hl != null) {
                highest = toBigDecimal(hl.get("high"));
                lowest = toBigDecimal(hl.get("low"));
                lastClose = toBigDecimal(hl.get("last_close"));
            }
        } catch (Exception e) {
            log.warn("[Feedback] 일봉 집계 실패: stockCode={}, error={}",
                    decision.getStockCode(), e.getMessage());
        }

        BigDecimal evaluatedPrice = lastClose != null ? lastClose : basePrice;

        BigDecimal returnRate = calculateDecisionReturnRate(decision, basePrice, evaluatedPrice);

        Boolean targetReached = null;
        Boolean stopLossReached = null;
        if ("BUY".equals(decision.getDecision())) {
            if (decision.getTargetPrice() != null && highest != null) {
                targetReached = highest.compareTo(decision.getTargetPrice()) >= 0;
            }
            if (decision.getStopLossPrice() != null && lowest != null) {
                stopLossReached = lowest.compareTo(decision.getStopLossPrice()) <= 0;
            }
        } else if ("SELL".equals(decision.getDecision())) {
            if (decision.getTargetPrice() != null && lowest != null) {
                targetReached = lowest.compareTo(decision.getTargetPrice()) <= 0;
            }
            if (decision.getStopLossPrice() != null && highest != null) {
                stopLossReached = highest.compareTo(decision.getStopLossPrice()) >= 0;
            }
        }

        Boolean success = evaluateDirectionalSuccess(decision, basePrice, evaluatedPrice, stopLossReached, targetReached);

        String summary = buildHoldingEndSummary(decision, returnRate, highest, lowest,
                targetReached, stopLossReached, success);

        AiFeedback feedback = AiFeedback.builder()
                .aiDecisionId(aiDecisionId)
                .stockCode(decision.getStockCode())
                .evaluationType("HOLDING_END")
                .basePrice(basePrice)
                .evaluatedPrice(evaluatedPrice)
                .highestPrice(highest)
                .lowestPrice(lowest)
                .returnRate(returnRate)
                .targetReached(targetReached)
                .stopLossReached(stopLossReached)
                .success(success)
                .feedbackSummary(summary)
                .build();

        aiFeedbackMapper.insertIgnore(feedback);
        log.info("[Feedback] HOLDING_END 저장: aiDecisionId={}, returnRate={}%, success={}",
                aiDecisionId, returnRate, success);
        return feedback;
    }

    private String buildHoldingEndSummary(
            AiDecision d, BigDecimal returnRate, BigDecimal high, BigDecimal low,
            Boolean targetReached, Boolean stopLossReached, Boolean success) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s %s] %d일 보유 종료",
                d.getDecision(), d.getStockCode(),
                d.getExpectedHoldingDays() == null ? 0 : d.getExpectedHoldingDays()));
        if (returnRate != null) sb.append(String.format(": 수익률 %+.2f%%", returnRate));
        if (high != null) sb.append(String.format(", 최고 %.0f", high));
        if (low != null) sb.append(String.format(", 최저 %.0f", low));
        if (Boolean.TRUE.equals(targetReached)) sb.append(" / 목표가 도달 O");
        else if (Boolean.FALSE.equals(targetReached)) sb.append(" / 목표가 미도달");
        if (Boolean.TRUE.equals(stopLossReached)) sb.append(" / 손절가 도달");
        if (Boolean.TRUE.equals(success)) sb.append(" → 성공");
        else if (Boolean.FALSE.equals(success)) sb.append(" → 실패");
        return sb.toString();
    }

    private BigDecimal calculateDecisionReturnRate(
            AiDecision decision,
            BigDecimal basePrice,
            BigDecimal evaluatedPrice
    ) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0 || evaluatedPrice == null) {
            return null;
        }
        BigDecimal numerator = "SELL".equals(decision.getDecision())
                ? basePrice.subtract(evaluatedPrice)
                : evaluatedPrice.subtract(basePrice);
        return numerator
                .divide(basePrice, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private Boolean evaluateDirectionalSuccess(
            AiDecision decision,
            BigDecimal basePrice,
            BigDecimal evaluatedPrice,
            Boolean stopLossReached,
            Boolean targetReached
    ) {
        if (Boolean.TRUE.equals(stopLossReached)) {
            return false;
        }
        if (basePrice != null && evaluatedPrice != null) {
            if ("BUY".equals(decision.getDecision())) {
                return evaluatedPrice.compareTo(basePrice) > 0;
            }
            if ("SELL".equals(decision.getDecision())) {
                return evaluatedPrice.compareTo(basePrice) < 0;
            }
        }
        return targetReached;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        return null;
    }

    // =========================================================================
    // Plan 14 Phase 3 / Phase 4: WEEKLY / MONTHLY 피드백
    // =========================================================================

    /**
     * 주간 피드백 집계 + OpenAI 요약 생성 → ai_periodic_summary 저장
     * @param weekStart 주간 시작일 (월)
     * @param weekEnd   주간 종료일 (금)
     */
    @Transactional
    public AiPeriodicSummary evaluateWeekly(LocalDate weekStart, LocalDate weekEnd) {
        return evaluatePeriodic("WEEKLY", weekStart, weekEnd);
    }

    /**
     * 월간 피드백 집계 + OpenAI 요약 생성 → ai_periodic_summary 저장
     * @param monthStart 월 시작일 (1일)
     * @param monthEnd   월 종료일 (말일)
     */
    @Transactional
    public AiPeriodicSummary evaluateMonthly(LocalDate monthStart, LocalDate monthEnd) {
        return evaluatePeriodic("MONTHLY", monthStart, monthEnd);
    }

    private AiPeriodicSummary evaluatePeriodic(String type, LocalDate from, LocalDate to) {
        // 1. ai_feedback 통계
        Map<String, Object> agg = aiFeedbackMapper.aggregateStatsBetween(from, to);

        // 2. 기간 내 BUY/SELL 판단 분포
        List<AiDecision> decisions = aiDecisionMapper.findDecisionsBetween(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());

        Map<String, Integer> decisionDist = new LinkedHashMap<>();
        decisionDist.put("BUY", 0);
        decisionDist.put("SELL", 0);
        decisionDist.put("HOLD", 0);
        Map<String, int[]> confidenceBucket = new LinkedHashMap<>();
        confidenceBucket.put("0.7-0.8", new int[]{0, 0});
        confidenceBucket.put("0.8-0.9", new int[]{0, 0});
        confidenceBucket.put("0.9-1.0", new int[]{0, 0});
        for (AiDecision d : decisions) {
            decisionDist.merge(d.getDecision(), 1, Integer::sum);
            if (d.getConfidence() != null) {
                double c = d.getConfidence().doubleValue();
                String key = c >= 0.9 ? "0.9-1.0" : c >= 0.8 ? "0.8-0.9" : c >= 0.7 ? "0.7-0.8" : null;
                if (key != null) confidenceBucket.get(key)[0]++;
            }
        }

        // 3. stats_json 구성
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("period", Map.of("from", from.toString(), "to", to.toString()));
        stats.put("aggregate", agg != null ? agg : Map.of());
        stats.put("decisionDistribution", decisionDist);
        stats.put("confidenceBucketCount", confidenceBucket);
        stats.put("totalDecisions", decisions.size());

        String statsJson;
        try {
            statsJson = objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            statsJson = "{}";
            log.warn("[Feedback] stats_json 직렬화 실패: {}", e.getMessage());
        }

        // 4. OpenAI 요약 생성 (실패 시 폴백 텍스트)
        String llmSummary;
        try {
            String system = "너는 한국 주식 자동매매 시스템의 AI 판단 품질을 분석하는 분석가다. "
                    + "아래 통계 JSON을 보고 한국어로 5~8문장의 간결한 자연어 요약과 개선 제안을 작성해라. "
                    + "수치가 부족하면 '데이터 부족'으로 명시하고 추측하지 마라.";
            String user = "[" + type + " 통계]\n" + statsJson;
            OpenAiResponse resp = openAiDecisionClient.createTextCompletion(
                    system, user, openAiProperties.modelDecision());
            llmSummary = resp.extractText();
        } catch (Exception e) {
            log.warn("[Feedback] {} 요약 LLM 실패, 폴백: {}", type, e.getMessage());
            llmSummary = String.format("[%s %s ~ %s] 자동 요약 생성 실패. 판단 %d건.",
                    type, from, to, decisions.size());
        }

        AiPeriodicSummary summary = AiPeriodicSummary.builder()
                .summaryType(type)
                .periodStart(from)
                .periodEnd(to)
                .statsJson(statsJson)
                .llmSummary(llmSummary)
                .build();
        aiPeriodicSummaryMapper.insertIgnore(summary);
        log.info("[Feedback] {} 요약 저장: {} ~ {}, decisions={}", type, from, to, decisions.size());
        return summary;
    }
}

