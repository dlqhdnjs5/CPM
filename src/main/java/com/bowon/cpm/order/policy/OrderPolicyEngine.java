package com.bowon.cpm.order.policy;

import com.bowon.cpm.ai.domain.AiDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 주문 정책 엔진
 * 리스크 검증 통과 후 실제 주문 수량과 금액을 계산한다.
 *
 * 계산 공식:
 *   주문 기준 금액 = 총 평가자산 × AI 추천 비중
 *   실제 주문 가능 금액 = min(주문 기준 금액, 예수금)
 *   주문 수량 = floor(실제 주문 가능 금액 / 현재가)
 *
 * 주문 수량 < 1 이면 주문 불가 (0 반환)
 */
@Slf4j
@Component
public class OrderPolicyEngine {

    public record OrderCalculation(
            int quantity,
            BigDecimal orderAmount,
            BigDecimal unitPrice
    ) {
        /** 주문 가능 여부 */
        public boolean isOrderable() {
            return quantity >= 1;
        }
    }

    /**
     * BUY 주문 수량 계산
     *
     * @param decision      AI 판단 (추천 비중, 현재가 포함)
     * @param totalAsset    총 평가자산
     * @param availableCash 가용 예수금
     */
    public OrderCalculation calculate(
            AiDecision decision,
            BigDecimal totalAsset,
            BigDecimal availableCash
    ) {
        BigDecimal currentPrice = decision.getCurrentPrice();
        BigDecimal weight = decision.getRecommendedPortfolioWeight();

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[OrderPolicy] 현재가 없음: stockCode={}", decision.getStockCode());
            return new OrderCalculation(0, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // AI 추천 비중이 없으면 예수금 전액 기준으로 계산
        BigDecimal targetAmount;
        if (weight != null && totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            // 주문 기준 금액 = 총 평가자산 × AI 추천 비중
            targetAmount = totalAsset.multiply(weight);
        } else {
            targetAmount = availableCash;
        }

        // 실제 주문 가능 금액 = min(목표 금액, 예수금)
        BigDecimal orderAmount = targetAmount.min(availableCash);

        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[OrderPolicy] 주문 가능 금액 없음: stockCode={}, availableCash={}",
                    decision.getStockCode(), availableCash);
            return new OrderCalculation(0, BigDecimal.ZERO, currentPrice);
        }

        // 주문 수량 = floor(주문 가능 금액 / 현재가)
        int quantity = orderAmount.divide(currentPrice, 0, RoundingMode.FLOOR).intValue();

        // 비중 제한으로 수량이 0이 되었지만 예수금으로 1주는 살 수 있으면 1주 허용
        // 예: 총자산 61만원 × 18% = 11만원 < 삼성전자 31.7만원 → qty=0
        //     하지만 예수금 61만원 ≥ 31.7만원이므로 1주는 가능 → qty=1 허용
        if (quantity == 0 && availableCash.compareTo(currentPrice) >= 0) {
            log.info("[OrderPolicy] 비중 제한으로 qty=0이나 예수금으로 1주 가능 → 1주로 조정: " +
                            "stockCode={}, targetAmt={}, availableCash={}, price={}",
                    decision.getStockCode(), targetAmount, availableCash, currentPrice);
            quantity = 1;
        }

        BigDecimal actualAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));

        log.info("[OrderPolicy] 주문 계산: stockCode={}, weight={}, targetAmt={}, " +
                        "availableCash={}, orderAmt={}, price={}, qty={}",
                decision.getStockCode(), weight, targetAmount,
                availableCash, orderAmount, currentPrice, quantity);

        return new OrderCalculation(quantity, actualAmount, currentPrice);
    }
}

