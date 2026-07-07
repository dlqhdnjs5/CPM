package com.bowon.cpm.order.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.RiskGuardProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.executor.OrderExecutor;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.policy.BuyOrderPolicyEngine;
import com.bowon.cpm.order.policy.SellOrderPolicyEngine;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import com.bowon.cpm.risk.mapper.RiskPolicyConfigMapper;
import com.bowon.cpm.risk.rule.SellRiskManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @Test
    @DisplayName("BUY order is blocked when the latest passed risk check is stale")
    void buyOrderIsBlockedWhenRiskCheckIsStale() {
        BuyOrderPolicyEngine buyOrderPolicyEngine = mock(BuyOrderPolicyEngine.class);
        SellOrderPolicyEngine sellOrderPolicyEngine = mock(SellOrderPolicyEngine.class);
        SellRiskManager sellRiskManager = mock(SellRiskManager.class);
        OrderExecutor orderExecutor = mock(OrderExecutor.class);
        OrderRequestCreateService orderRequestCreateService = mock(OrderRequestCreateService.class);
        OrderRequestMapper orderRequestMapper = mock(OrderRequestMapper.class);
        AiDecisionMapper aiDecisionMapper = mock(AiDecisionMapper.class);
        RiskCheckResultMapper riskCheckResultMapper = mock(RiskCheckResultMapper.class);
        RiskPolicyConfigMapper riskPolicyConfigMapper = mock(RiskPolicyConfigMapper.class);
        PortfolioPositionMapper portfolioPositionMapper = mock(PortfolioPositionMapper.class);
        PaperPortfolioService paperPortfolioService = mock(PaperPortfolioService.class);
        BrokerClient brokerClient = mock(BrokerClient.class);
        KisProperties kisProperties = new KisProperties(null, null, "key", "secret", "12345678", "01",
                "/token", "approval");
        TradingProperties tradingProperties = new TradingProperties("PAPER", true);
        RiskGuardProperties riskGuardProperties = new RiskGuardProperties(
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
        );

        AiDecision decision = AiDecision.builder()
                .id(77L)
                .stockCode("005930")
                .decision("BUY")
                .currentPrice(new BigDecimal("70000"))
                .stopLossPrice(new BigDecimal("65000"))
                .recommendedPortfolioWeight(new BigDecimal("0.10"))
                .build();
        RiskCheckResult stalePass = RiskCheckResult.builder()
                .aiDecisionId(77L)
                .tradingMode("PAPER")
                .passed(true)
                .checkedAt(LocalDateTime.now().minusMinutes(11))
                .build();

        when(aiDecisionMapper.findById(77L)).thenReturn(Optional.of(decision));
        when(riskCheckResultMapper.findLatestByAiDecisionIdAndTradingMode(77L, "PAPER"))
                .thenReturn(Optional.of(stalePass));

        OrderService service = new OrderService(
                buyOrderPolicyEngine,
                sellOrderPolicyEngine,
                sellRiskManager,
                orderExecutor,
                orderRequestCreateService,
                orderRequestMapper,
                aiDecisionMapper,
                riskCheckResultMapper,
                riskPolicyConfigMapper,
                portfolioPositionMapper,
                paperPortfolioService,
                brokerClient,
                kisProperties,
                tradingProperties,
                riskGuardProperties
        );

        assertThatThrownBy(() -> service.placeOrder(77L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("risk check expired");

        verify(orderRequestCreateService, never()).createReady(org.mockito.ArgumentMatchers.any());
        verify(orderExecutor, never()).execute(org.mockito.ArgumentMatchers.any());
    }
}
