package com.bowon.cpm.order.policy;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SellOrderPolicyEngineTest {

    private final SellOrderPolicyEngine engine = new SellOrderPolicyEngine();

    @Test
    @DisplayName("AI SELL weight zero sells all held quantity")
    void aiSellWeightZeroSellsAll() {
        SellOrderPolicyEngine.SellCalculation calc = engine.calculate(
                decision("SELL", "0"),
                position(10, 10),
                new BigDecimal("10000"),
                new BigDecimal("1000000"),
                SellTrigger.AI_DECISION,
                false
        );

        assertThat(calc.quantity()).isEqualTo(10);
        assertThat(calc.orderAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(calc.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("AI SELL positive weight reduces to target remaining quantity")
    void aiSellPositiveWeightReducesToTargetQuantity() {
        SellOrderPolicyEngine.SellCalculation calc = engine.calculate(
                decision("SELL", "0.50"),
                position(10, 10),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                SellTrigger.AI_DECISION,
                false
        );

        assertThat(calc.quantity()).isEqualTo(5);
        assertThat(calc.orderAmount()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("First target hit sells half and keeps at least one share")
    void firstTargetHitSellsHalfMinimumOne() {
        SellOrderPolicyEngine.SellCalculation threeShares = engine.calculate(
                decision("BUY", "0.10"),
                position(3, 3),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                SellTrigger.TARGET_HIT_1,
                false
        );
        SellOrderPolicyEngine.SellCalculation oneShare = engine.calculate(
                decision("BUY", "0.10"),
                position(1, 1),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                SellTrigger.TARGET_HIT_1,
                false
        );

        assertThat(threeShares.quantity()).isEqualTo(1);
        assertThat(oneShare.quantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Second target, stop loss, or breakeven protection sells all remaining quantity")
    void secondTargetStopLossAndBreakevenSellAll() {
        SellOrderPolicyEngine.SellCalculation target2 = engine.calculate(
                decision("BUY", "0.10"),
                position(7, 7),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                SellTrigger.TARGET_HIT_2,
                true
        );
        SellOrderPolicyEngine.SellCalculation stopLoss = engine.calculate(
                decision("BUY", "0.10"),
                position(7, 7),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                SellTrigger.STOP_LOSS_HIT,
                false
        );
        SellOrderPolicyEngine.SellCalculation breakeven = engine.calculate(
                decision("BUY", "0.10"),
                position(7, 7),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                SellTrigger.BREAKEVEN_PROTECT,
                false
        );

        assertThat(target2.quantity()).isEqualTo(7);
        assertThat(stopLoss.quantity()).isEqualTo(7);
        assertThat(breakeven.quantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("AI SELL without recommended weight is not orderable")
    void aiSellWithoutWeightIsNotOrderable() {
        SellOrderPolicyEngine.SellCalculation calc = engine.calculate(
                decision("SELL", null),
                position(10, 10),
                new BigDecimal("10000"),
                new BigDecimal("100000"),
                SellTrigger.AI_DECISION,
                false
        );

        assertThat(calc.quantity()).isZero();
        assertThat(calc.isOrderable()).isFalse();
    }

    private AiDecision decision(String decision, String weight) {
        return AiDecision.builder()
                .id(1L)
                .stockCode("005930")
                .decision(decision)
                .confidence(new BigDecimal("0.80"))
                .recommendedPortfolioWeight(weight != null ? new BigDecimal(weight) : null)
                .build();
    }

    private PortfolioPosition position(int quantity, int availableQuantity) {
        return PortfolioPosition.builder()
                .accountNo("12345678")
                .stockCode("005930")
                .quantity(quantity)
                .availableQuantity(availableQuantity)
                .averageBuyPrice(new BigDecimal("9000"))
                .build();
    }
}
