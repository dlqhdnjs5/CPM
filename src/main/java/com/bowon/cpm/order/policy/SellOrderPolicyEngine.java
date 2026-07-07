package com.bowon.cpm.order.policy;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
public class SellOrderPolicyEngine {

    public record SellCalculation(
            int quantity,
            BigDecimal orderAmount,
            BigDecimal unitPrice
    ) {
        public boolean isOrderable() {
            return quantity >= 1;
        }
    }

    public SellCalculation calculate(
            AiDecision decision,
            PortfolioPosition position,
            BigDecimal currentPrice,
            BigDecimal totalAsset,
            SellTrigger trigger,
            boolean hasPreviousPartialSell
    ) {
        if (position == null || position.getQuantity() == null || position.getQuantity() <= 0) {
            return new SellCalculation(0, BigDecimal.ZERO, safePrice(currentPrice));
        }
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[SellPolicy] 현재가 없음: stockCode={}", decision.getStockCode());
            return new SellCalculation(0, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        int heldQuantity = position.getQuantity();
        int quantity = switch (trigger) {
            case AI_DECISION -> calculateAiSellQuantity(decision, heldQuantity, currentPrice, totalAsset);
            case TARGET_HIT_1 -> hasPreviousPartialSell
                    ? heldQuantity
                    : Math.max(1, heldQuantity / 2);
            case TARGET_HIT_2, STOP_LOSS_HIT, BREAKEVEN_PROTECT -> heldQuantity;
        };

        if (quantity < 1) {
            return new SellCalculation(0, BigDecimal.ZERO, currentPrice);
        }

        BigDecimal amount = currentPrice.multiply(BigDecimal.valueOf(quantity));
        log.info("[SellPolicy] 매도 계산: stockCode={}, trigger={}, heldQty={}, sellQty={}, price={}, amount={}",
                decision.getStockCode(), trigger, heldQuantity, quantity, currentPrice, amount);
        return new SellCalculation(quantity, amount, currentPrice);
    }

    private int calculateAiSellQuantity(
            AiDecision decision,
            int heldQuantity,
            BigDecimal currentPrice,
            BigDecimal totalAsset
    ) {
        BigDecimal weight = decision.getRecommendedPortfolioWeight();
        if (weight == null) {
            return 0;
        }
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            return heldQuantity;
        }
        if (totalAsset == null || totalAsset.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        BigDecimal targetAmount = totalAsset.multiply(weight);
        int targetQuantity = targetAmount.divide(currentPrice, 0, RoundingMode.FLOOR).intValue();
        return Math.max(0, heldQuantity - targetQuantity);
    }

    private BigDecimal safePrice(BigDecimal price) {
        return price != null ? price : BigDecimal.ZERO;
    }
}
