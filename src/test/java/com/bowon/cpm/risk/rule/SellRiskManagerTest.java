package com.bowon.cpm.risk.rule;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SellRiskManagerTest {

    private final SellRiskManager manager = new SellRiskManager();

    @Test
    @DisplayName("Fails when no position exists")
    void failsWithoutPosition() {
        String result = manager.check(decision("SELL", "0.80"), policy(), null, 1, SellTrigger.AI_DECISION);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Fails when requested quantity is below one")
    void failsBelowMinimumQuantity() {
        String result = manager.check(decision("SELL", "0.80"), policy(), position(10, 10), 0, SellTrigger.AI_DECISION);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Fails when requested quantity exceeds held quantity")
    void failsWhenRequestedQuantityExceedsHeldQuantity() {
        String result = manager.check(decision("SELL", "0.80"), policy(), position(10, 10), 11, SellTrigger.AI_DECISION);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Fails when requested quantity exceeds available quantity")
    void failsWhenRequestedQuantityExceedsAvailableQuantity() {
        String result = manager.check(decision("SELL", "0.80"), policy(), position(10, 4), 5, SellTrigger.AI_DECISION);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Fails low confidence only for AI SELL decision")
    void failsLowConfidenceForAiSell() {
        String result = manager.check(decision("SELL", "0.60"), policy(), position(10, 10), 5, SellTrigger.AI_DECISION);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Target and stop triggers pass without AI SELL confidence")
    void targetAndStopTriggersPassWithoutAiSellConfidence() {
        String targetResult = manager.check(decision("BUY", "0.10"), policy(), position(10, 10), 5, SellTrigger.TARGET_HIT_1);
        String stopResult = manager.check(decision("BUY", "0.10"), policy(), position(10, 10), 10, SellTrigger.STOP_LOSS_HIT);

        assertThat(targetResult).isNull();
        assertThat(stopResult).isNull();
    }

    private AiDecision decision(String decision, String confidence) {
        return AiDecision.builder()
                .id(1L)
                .stockCode("005930")
                .decision(decision)
                .confidence(new BigDecimal(confidence))
                .build();
    }

    private PortfolioPosition position(int quantity, int availableQuantity) {
        return PortfolioPosition.builder()
                .accountNo("12345678")
                .stockCode("005930")
                .quantity(quantity)
                .availableQuantity(availableQuantity)
                .build();
    }

    private RiskPolicyConfig policy() {
        return RiskPolicyConfig.builder()
                .policyCode("DEFAULT_RISK_POLICY")
                .minConfidence(new BigDecimal("0.70"))
                .build();
    }
}
