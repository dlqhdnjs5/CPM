package com.bowon.cpm.order.policy;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.common.config.RiskGuardProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BUY order sizing policy.
 *
 * Base cap: min(total asset * AI recommended weight, available cash).
 * Risk cap: max loss per trade / loss per share, based on AI stop loss.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuyOrderPolicyEngine {

    private final RiskGuardProperties riskGuardProperties;

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
            log.warn("[BuyPolicy] current price missing: stockCode={}", decision.getStockCode());
            return new OrderCalculation(0, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal rawAvailableCash = availableCash != null ? availableCash : BigDecimal.ZERO;
        BigDecimal reserveAmount = calculateCashReserve(totalAsset);
        BigDecimal safeAvailableCash = rawAvailableCash.subtract(reserveAmount).max(BigDecimal.ZERO);
        BigDecimal targetAmount;
        if (weight != null && totalAsset != null && totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            targetAmount = totalAsset.multiply(weight);
        } else {
            targetAmount = safeAvailableCash;
        }

        BigDecimal cappedAmount = targetAmount.min(safeAvailableCash);
        if (cappedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[BuyPolicy] no orderable cash after reserve: stockCode={}, availableCash={}, reserve={}",
                    decision.getStockCode(), rawAvailableCash, reserveAmount);
            return new OrderCalculation(0, BigDecimal.ZERO, currentPrice);
        }

        int cashWeightQuantity = cappedAmount.divide(currentPrice, 0, RoundingMode.FLOOR).intValue();
        int riskQuantity = calculateRiskQuantity(decision, totalAsset);
        int quantity = Math.min(cashWeightQuantity, riskQuantity);

        BigDecimal actualAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));
        log.info("[BuyPolicy] calculated: stockCode={}, weight={}, targetAmount={}, availableCash={}, " +
                        "reserveAmount={}, orderableCash={}, cappedAmount={}, cashWeightQty={}, riskQty={}, price={}, qty={}",
                decision.getStockCode(), weight, targetAmount, rawAvailableCash,
                reserveAmount, safeAvailableCash,
                cappedAmount, cashWeightQuantity, riskQuantity, currentPrice, quantity);

        return new OrderCalculation(quantity, actualAmount, currentPrice);
    }

    private BigDecimal calculateCashReserve(BigDecimal totalAsset) {
        BigDecimal reserveRate = riskGuardProperties.minCashReserveRate();
        if (totalAsset == null || totalAsset.compareTo(BigDecimal.ZERO) <= 0
                || reserveRate == null || reserveRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalAsset.multiply(reserveRate);
    }

    private int calculateRiskQuantity(AiDecision decision, BigDecimal totalAsset) {
        BigDecimal currentPrice = decision.getCurrentPrice();
        BigDecimal stopLossPrice = decision.getStopLossPrice();
        BigDecimal riskRate = riskGuardProperties.maxRiskPerTradeRate();

        if (totalAsset == null || totalAsset.compareTo(BigDecimal.ZERO) <= 0
                || stopLossPrice == null || stopLossPrice.compareTo(BigDecimal.ZERO) <= 0
                || currentPrice == null || currentPrice.compareTo(stopLossPrice) <= 0
                || riskRate == null || riskRate.compareTo(BigDecimal.ZERO) <= 0) {
            return Integer.MAX_VALUE;
        }

        BigDecimal lossPerShare = currentPrice.subtract(stopLossPrice);
        BigDecimal riskBudget = totalAsset.multiply(riskRate);
        int riskQuantity = riskBudget.divide(lossPerShare, 0, RoundingMode.FLOOR).intValue();
        log.info("[BuyPolicy] risk cap: stockCode={}, riskBudget={}, lossPerShare={}, riskQty={}",
                decision.getStockCode(), riskBudget, lossPerShare, riskQuantity);
        return Math.max(riskQuantity, 0);
    }
}
