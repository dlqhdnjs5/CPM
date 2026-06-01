package com.bowon.cpm.feedback.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.PortfolioProfitLoss;
import com.bowon.cpm.feedback.mapper.AiFeedbackMapper;
import com.bowon.cpm.feedback.mapper.PortfolioProfitLossMapper;
import com.bowon.cpm.portfolio.domain.AccountBalance;
import com.bowon.cpm.portfolio.mapper.AccountBalanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

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
        BigDecimal returnRate = null;
        if (basePrice != null && basePrice.compareTo(BigDecimal.ZERO) > 0 && currentPrice != null) {
            returnRate = currentPrice.subtract(basePrice)
                    .divide(basePrice, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(4, RoundingMode.HALF_UP);
        }

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
        Boolean success = null;
        if (targetReached != null && stopLossReached != null) {
            success = targetReached && !stopLossReached;
        } else if (targetReached != null) {
            success = targetReached;
        }

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
}

