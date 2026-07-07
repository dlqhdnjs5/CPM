package com.bowon.cpm.order.policy;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.common.config.RiskGuardProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BuyOrderPolicyEngineTest {

    private final BuyOrderPolicyEngine engine = new BuyOrderPolicyEngine(
            new RiskGuardProperties(
                    new BigDecimal("0.005"),
                    new BigDecimal("0.015"),
                    2,
                    24,
                    10,
                    new BigDecimal("-1.5"),
                    new BigDecimal("-2.5"),
                    48,
                    3,
                    new BigDecimal("0.002"),
                    new BigDecimal("75"),
                    new BigDecimal("0.12"),
                    new BigDecimal("0.03"),
                    new BigDecimal("0.20"),
                    8,
                    new BigDecimal("0.35")
            )
    );

    @Test
    @DisplayName("BUY quantity is capped by max risk per trade")
    void buyQuantityIsCappedByRiskBudget() {
        AiDecision decision = AiDecision.builder()
                .stockCode("005930")
                .currentPrice(new BigDecimal("100000"))
                .stopLossPrice(new BigDecimal("90000"))
                .recommendedPortfolioWeight(new BigDecimal("0.20"))
                .build();

        BuyOrderPolicyEngine.OrderCalculation result = engine.calculate(
                decision,
                new BigDecimal("10000000"),
                new BigDecimal("10000000")
        );

        assertThat(result.quantity()).isEqualTo(5);
        assertThat(result.orderAmount()).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("BUY quantity preserves configured cash reserve")
    void buyQuantityPreservesCashReserve() {
        AiDecision decision = AiDecision.builder()
                .stockCode("035420")
                .currentPrice(new BigDecimal("100000"))
                .stopLossPrice(new BigDecimal("99000"))
                .recommendedPortfolioWeight(new BigDecimal("1.00"))
                .build();

        BuyOrderPolicyEngine.OrderCalculation result = engine.calculate(
                decision,
                new BigDecimal("10000000"),
                new BigDecimal("3000000")
        );

        assertThat(result.quantity()).isEqualTo(10);
        assertThat(result.orderAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("BUY does not force one share when risk budget allows zero shares")
    void buyDoesNotForceOneShareWhenRiskBudgetIsTooSmall() {
        AiDecision decision = AiDecision.builder()
                .stockCode("000660")
                .currentPrice(new BigDecimal("200000"))
                .stopLossPrice(new BigDecimal("100000"))
                .recommendedPortfolioWeight(new BigDecimal("0.20"))
                .build();

        BuyOrderPolicyEngine.OrderCalculation result = engine.calculate(
                decision,
                new BigDecimal("10000000"),
                new BigDecimal("10000000")
        );

        assertThat(result.quantity()).isZero();
        assertThat(result.isOrderable()).isFalse();
    }
}
