package com.bowon.cpm.risk.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.LiquidityProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.portfolio.service.PortfolioService;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import com.bowon.cpm.risk.mapper.RiskPolicyConfigMapper;
import com.bowon.cpm.risk.rule.RiskManager;
import com.bowon.cpm.risk.rule.SellRiskManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    @Test
    @DisplayName("BUY risk check fails when 20 day average trading value is below liquidity threshold")
    void buyFailsWhenLiquidityIsTooLow() {
        RiskManager riskManager = mock(RiskManager.class);
        SellRiskManager sellRiskManager = mock(SellRiskManager.class);
        RiskPolicyConfigMapper riskPolicyConfigMapper = mock(RiskPolicyConfigMapper.class);
        RiskCheckResultMapper riskCheckResultMapper = mock(RiskCheckResultMapper.class);
        AiDecisionMapper aiDecisionMapper = mock(AiDecisionMapper.class);
        PortfolioPositionMapper portfolioPositionMapper = mock(PortfolioPositionMapper.class);
        PaperPortfolioService paperPortfolioService = mock(PaperPortfolioService.class);
        BrokerClient brokerClient = mock(BrokerClient.class);
        PortfolioService portfolioService = mock(PortfolioService.class);
        StockPriceDailyMapper stockPriceDailyMapper = mock(StockPriceDailyMapper.class);

        TradingProperties tradingProperties = new TradingProperties("PAPER", true);
        LiquidityProperties liquidityProperties = new LiquidityProperties(100_000L, 3_000_000_000L);
        KisProperties kisProperties = new KisProperties(null, null, "key", "secret", "12345678", "01",
                "/token", "approval");

        AiDecision decision = AiDecision.builder()
                .id(10L)
                .stockCode("000950")
                .stockName("전방")
                .decision("BUY")
                .confidence(new BigDecimal("0.90"))
                .currentPrice(new BigDecimal("30000"))
                .targetPrice(new BigDecimal("33000"))
                .stopLossPrice(new BigDecimal("28500"))
                .riskRewardRatio(new BigDecimal("2.0"))
                .recommendedPortfolioWeight(new BigDecimal("0.1"))
                .build();

        when(aiDecisionMapper.findById(10L)).thenReturn(Optional.of(decision));
        when(riskPolicyConfigMapper.findByPolicyCode("DEFAULT_RISK_POLICY")).thenReturn(Optional.of(
                RiskPolicyConfig.builder()
                        .policyCode("DEFAULT_RISK_POLICY")
                        .minConfidence(new BigDecimal("0.7"))
                        .maxPositionWeight(new BigDecimal("0.2"))
                        .minRiskRewardRatio(new BigDecimal("1.2"))
                        .maxExpectedLossRate(new BigDecimal("-0.07"))
                        .build()
        ));
        when(brokerClient.getAccountBalance()).thenReturn(AccountBalanceResult.builder()
                .availableCash(new BigDecimal("10000000"))
                .totalAssetAmount(new BigDecimal("100000000"))
                .build());
        when(paperPortfolioService.findLatestAccountBalance("12345678")).thenReturn(Optional.of(
                PaperAccountBalance.builder()
                        .availableCash(new BigDecimal("10000000"))
                        .totalAssetAmount(new BigDecimal("100000000"))
                        .build()
        ));
        when(stockPriceDailyMapper.findRecentLiquidityAverage("000950", 20)).thenReturn(Map.of(
                "average_volume", new BigDecimal("200000"),
                "average_trading_value", new BigDecimal("500000000")
        ));

        RiskService service = new RiskService(
                riskManager,
                sellRiskManager,
                riskPolicyConfigMapper,
                riskCheckResultMapper,
                aiDecisionMapper,
                portfolioPositionMapper,
                paperPortfolioService,
                kisProperties,
                brokerClient,
                tradingProperties,
                portfolioService,
                liquidityProperties,
                stockPriceDailyMapper
        );

        RiskCheckResult result = service.checkAndSave(10L);

        assertThat(result.getPassed()).isFalse();
        assertThat(result.getFailReason()).contains("20일 평균 거래대금");

        ArgumentCaptor<RiskCheckResult> captor = ArgumentCaptor.forClass(RiskCheckResult.class);
        verify(riskCheckResultMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassed()).isFalse();
        assertThat(captor.getValue().getFailReason()).contains("유동성 부족");
    }
}
