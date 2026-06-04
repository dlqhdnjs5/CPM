package com.bowon.cpm.order.policy;

import com.bowon.cpm.ai.domain.AiDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BUY 주문 정책 엔진.
 *
 * 계산 공식:
 * 주문 기준 금액 = 총 평가자산 x AI 추천 비중
 * 실제 주문 가능 금액 = min(주문 기준 금액, 예수금)
 * 주문 수량 = floor(실제 주문 가능 금액 / 현재가)
 */
@Slf4j
@Component
public class BuyOrderPolicyEngine {

    public record OrderCalculation(
            int quantity,
            BigDecimal orderAmount,
            BigDecimal unitPrice
    ) {
        public boolean isOrderable() {
            return quantity >= 1;
        }
    }

    public OrderCalculation calculate(
            AiDecision decision,
            BigDecimal totalAsset,
            BigDecimal availableCash
    ) {
        BigDecimal currentPrice = decision.getCurrentPrice();
        BigDecimal weight = decision.getRecommendedPortfolioWeight();

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[BuyPolicy] 현재가 없음: stockCode={}", decision.getStockCode());
            return new OrderCalculation(0, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal safeAvailableCash = availableCash != null ? availableCash : BigDecimal.ZERO;
        BigDecimal targetAmount;
        if (weight != null && totalAsset != null && totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            targetAmount = totalAsset.multiply(weight);
        } else {
            targetAmount = safeAvailableCash;
        }

        BigDecimal orderAmount = targetAmount.min(safeAvailableCash);
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[BuyPolicy] 주문 가능 금액 없음: stockCode={}, availableCash={}",
                    decision.getStockCode(), safeAvailableCash);
            return new OrderCalculation(0, BigDecimal.ZERO, currentPrice);
        }

        int quantity = orderAmount.divide(currentPrice, 0, RoundingMode.FLOOR).intValue();
        if (quantity == 0 && safeAvailableCash.compareTo(currentPrice) >= 0) {
            log.info("[BuyPolicy] 비중 제한으로 qty=0이나 예수금으로 1주 가능 → 1주로 조정: " +
                            "stockCode={}, targetAmt={}, availableCash={}, price={}",
                    decision.getStockCode(), targetAmount, safeAvailableCash, currentPrice);
            quantity = 1;
        }

        BigDecimal actualAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));
        log.info("[BuyPolicy] 주문 계산: stockCode={}, weight={}, targetAmt={}, " +
                        "availableCash={}, orderAmt={}, price={}, qty={}",
                decision.getStockCode(), weight, targetAmount,
                safeAvailableCash, orderAmount, currentPrice, quantity);

        return new OrderCalculation(quantity, actualAmount, currentPrice);
    }
}
