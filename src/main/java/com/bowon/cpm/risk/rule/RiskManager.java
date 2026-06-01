package com.bowon.cpm.risk.rule;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 룰 기반 리스크 검증기
 *
 * AI 판단 결과를 정책(RiskPolicyConfig) 기준으로 검증한다.
 * 검증 실패 시 실패 사유 문자열 반환, 통과 시 null 반환.
 *
 * 주문 흐름:
 * AI Decision → RiskManager.check() → 통과 시 OrderRequest 생성
 */
@Slf4j
@Component
public class RiskManager {

    /**
     * 리스크 검증 수행
     *
     * @param decision       AI 판단
     * @param policy         리스크 정책
     * @param availableCash  현재 예수금
     * @param totalAsset     총 평가 자산
     * @param currentPositionAmount 해당 종목 현재 보유 평가금액
     * @return null = 통과, 문자열 = 실패 사유
     */
    public String check(
            AiDecision decision,
            RiskPolicyConfig policy,
            BigDecimal availableCash,
            BigDecimal totalAsset,
            BigDecimal currentPositionAmount
    ) {
        // HOLD는 주문 없음 → 리스크 체크 불필요, 통과 처리
        if ("HOLD".equals(decision.getDecision())) {
            log.debug("[Risk] HOLD 판단 → 리스크 체크 생략: stockCode={}", decision.getStockCode());
            return null;
        }

        // 1. AI 신뢰도 검증
        if (decision.getConfidence() == null ||
                decision.getConfidence().compareTo(policy.getMinConfidence()) < 0) {
            return String.format("AI 신뢰도 부족: confidence=%.4f < min=%.4f",
                    decision.getConfidence(), policy.getMinConfidence());
        }

        // 2. BUY 시 목표가/손절가 방향 검증
        if ("BUY".equals(decision.getDecision())) {
            if (decision.getTargetPrice() != null && decision.getCurrentPrice() != null
                    && decision.getTargetPrice().compareTo(decision.getCurrentPrice()) <= 0) {
                return String.format("목표가가 현재가 이하: target=%.0f <= current=%.0f",
                        decision.getTargetPrice(), decision.getCurrentPrice());
            }
            if (decision.getStopLossPrice() != null && decision.getCurrentPrice() != null
                    && decision.getStopLossPrice().compareTo(decision.getCurrentPrice()) >= 0) {
                return String.format("손절가가 현재가 이상: stopLoss=%.0f >= current=%.0f",
                        decision.getStopLossPrice(), decision.getCurrentPrice());
            }
        }

        // 3. 손익비 검증
        if (decision.getRiskRewardRatio() != null && policy.getMinRiskRewardRatio() != null
                && decision.getRiskRewardRatio().compareTo(policy.getMinRiskRewardRatio()) < 0) {
            return String.format("손익비 부족: riskReward=%.4f < min=%.4f",
                    decision.getRiskRewardRatio(), policy.getMinRiskRewardRatio());
        }

        // 4. 예상 손실률 검증 (expectedLossRate는 음수, maxExpectedLossRate도 음수)
        if (decision.getExpectedLossRate() != null && policy.getMaxExpectedLossRate() != null
                && decision.getExpectedLossRate().compareTo(policy.getMaxExpectedLossRate()) < 0) {
            return String.format("예상 손실률 초과: lossRate=%.4f < max=%.4f",
                    decision.getExpectedLossRate(), policy.getMaxExpectedLossRate());
        }

        // 5. 예수금 부족 검증 (최소 1주 이상 살 수 있는지)
        if (availableCash != null && decision.getCurrentPrice() != null
                && availableCash.compareTo(decision.getCurrentPrice()) < 0) {
            return String.format("예수금 부족: cash=%.0f < price=%.0f",
                    availableCash, decision.getCurrentPrice());
        }

        // 6. 종목별 최대 비중 검증
        if (totalAsset != null && totalAsset.compareTo(BigDecimal.ZERO) > 0
                && currentPositionAmount != null && policy.getMaxPositionWeight() != null) {
            BigDecimal currentWeight = currentPositionAmount.divide(totalAsset, 4, BigDecimal.ROUND_HALF_UP);
            if (currentWeight.compareTo(policy.getMaxPositionWeight()) >= 0) {
                return String.format("종목 최대 비중 초과: currentWeight=%.4f >= max=%.4f",
                        currentWeight, policy.getMaxPositionWeight());
            }
        }

        // 7. 최대 주문 금액 검증
        if (policy.getMaxOrderAmount() != null && availableCash != null
                && decision.getRecommendedPortfolioWeight() != null && totalAsset != null) {
            BigDecimal expectedOrderAmt = totalAsset.multiply(decision.getRecommendedPortfolioWeight());
            if (expectedOrderAmt.compareTo(policy.getMaxOrderAmount()) > 0) {
                return String.format("최대 주문 금액 초과: expected=%.0f > max=%.0f",
                        expectedOrderAmt, policy.getMaxOrderAmount());
            }
        }

        return null; // 모든 검증 통과
    }
}

