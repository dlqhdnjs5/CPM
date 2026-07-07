package com.bowon.cpm.risk.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.LiquidityProperties;
import com.bowon.cpm.common.config.RiskGuardProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.feedback.mapper.PortfolioRealizedProfitLossMapper;
import com.bowon.cpm.market.domain.MarketContext;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.mapper.StockIndicatorDailyMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.market.service.MarketContextService;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
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
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        OrderRequestMapper orderRequestMapper = mock(OrderRequestMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);
        MarketContextService marketContextService = mock(MarketContextService.class);
        PortfolioRealizedProfitLossMapper realizedProfitLossMapper = mock(PortfolioRealizedProfitLossMapper.class);
        StockIndicatorDailyMapper stockIndicatorDailyMapper = mock(StockIndicatorDailyMapper.class);

        TradingProperties tradingProperties = new TradingProperties("PAPER", true);
        LiquidityProperties liquidityProperties = new LiquidityProperties(100_000L, 3_000_000_000L);
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
                .totalAssetAmount(new BigDecimal("30000000"))
                .build());
        when(paperPortfolioService.findLatestAccountBalance("12345678")).thenReturn(Optional.of(
                PaperAccountBalance.builder()
                        .availableCash(new BigDecimal("10000000"))
                        .totalAssetAmount(new BigDecimal("30000000"))
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
                stockPriceDailyMapper,
                riskGuardProperties,
                orderRequestMapper,
                stockMasterMapper,
                marketContextService,
                realizedProfitLossMapper,
                stockIndicatorDailyMapper
        );

        RiskCheckResult result = service.checkAndSave(10L);

        assertThat(result.getPassed()).isFalse();
        assertThat(result.getFailReason()).contains("20일 평균 거래대금");

        ArgumentCaptor<RiskCheckResult> captor = ArgumentCaptor.forClass(RiskCheckResult.class);
        verify(riskCheckResultMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassed()).isFalse();
        assertThat(captor.getValue().getFailReason()).contains("유동성 부족");
    }

    @Test
    @DisplayName("BUY risk check fails when the stock market regime is weak")
    void buyFailsWhenMarketRegimeIsWeak() {
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
        OrderRequestMapper orderRequestMapper = mock(OrderRequestMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);
        MarketContextService marketContextService = mock(MarketContextService.class);
        PortfolioRealizedProfitLossMapper realizedProfitLossMapper = mock(PortfolioRealizedProfitLossMapper.class);
        StockIndicatorDailyMapper stockIndicatorDailyMapper = mock(StockIndicatorDailyMapper.class);

        TradingProperties tradingProperties = new TradingProperties("PAPER", true);
        LiquidityProperties liquidityProperties = new LiquidityProperties(100_000L, 3_000_000_000L);
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
        KisProperties kisProperties = new KisProperties(null, null, "key", "secret", "12345678", "01",
                "/token", "approval");

        AiDecision decision = AiDecision.builder()
                .id(11L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .decision("BUY")
                .confidence(new BigDecimal("0.90"))
                .currentPrice(new BigDecimal("70000"))
                .targetPrice(new BigDecimal("77000"))
                .stopLossPrice(new BigDecimal("66500"))
                .riskRewardRatio(new BigDecimal("2.0"))
                .recommendedPortfolioWeight(new BigDecimal("0.1"))
                .build();

        when(aiDecisionMapper.findById(11L)).thenReturn(Optional.of(decision));
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
                .totalAssetAmount(new BigDecimal("30000000"))
                .build());
        when(paperPortfolioService.findLatestAccountBalance("12345678")).thenReturn(Optional.of(
                PaperAccountBalance.builder()
                        .availableCash(new BigDecimal("10000000"))
                        .totalAssetAmount(new BigDecimal("30000000"))
                        .build()
        ));
        when(stockMasterMapper.findByStockCode("005930")).thenReturn(Optional.of(
                StockMaster.builder()
                        .stockCode("005930")
                        .stockName("Samsung Electronics")
                        .marketType("KOSPI")
                        .sectorName("Semiconductor")
                        .isActive(true)
                        .build()
        ));
        when(marketContextService.latestContext("KOSPI", "Semiconductor")).thenReturn(
                MarketContext.builder()
                        .marketType("KOSPI")
                        .sectorName("Semiconductor")
                        .kospiChangeRate(new BigDecimal("-2.00"))
                        .sectorChangeRate(new BigDecimal("-1.00"))
                        .build()
        );

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
                stockPriceDailyMapper,
                riskGuardProperties,
                orderRequestMapper,
                stockMasterMapper,
                marketContextService,
                realizedProfitLossMapper,
                stockIndicatorDailyMapper
        );

        RiskCheckResult result = service.checkAndSave(11L);

        assertThat(result.getPassed()).isFalse();
        assertThat(result.getFailReason()).contains("시장 약세 구간 BUY 차단");
    }

    @Test
    @DisplayName("BUY risk check fails when the same stock has recent realized loss")
    void buyFailsWhenSameStockHasRecentRealizedLoss() {
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
        OrderRequestMapper orderRequestMapper = mock(OrderRequestMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);
        MarketContextService marketContextService = mock(MarketContextService.class);
        PortfolioRealizedProfitLossMapper realizedProfitLossMapper = mock(PortfolioRealizedProfitLossMapper.class);
        StockIndicatorDailyMapper stockIndicatorDailyMapper = mock(StockIndicatorDailyMapper.class);

        TradingProperties tradingProperties = new TradingProperties("PAPER", true);
        LiquidityProperties liquidityProperties = new LiquidityProperties(100_000L, 3_000_000_000L);
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
        KisProperties kisProperties = new KisProperties(null, null, "key", "secret", "12345678", "01",
                "/token", "approval");

        AiDecision decision = AiDecision.builder()
                .id(12L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .decision("BUY")
                .confidence(new BigDecimal("0.90"))
                .currentPrice(new BigDecimal("70000"))
                .targetPrice(new BigDecimal("77000"))
                .stopLossPrice(new BigDecimal("66500"))
                .riskRewardRatio(new BigDecimal("2.0"))
                .recommendedPortfolioWeight(new BigDecimal("0.1"))
                .build();

        when(aiDecisionMapper.findById(12L)).thenReturn(Optional.of(decision));
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
                .totalAssetAmount(new BigDecimal("30000000"))
                .build());
        when(paperPortfolioService.findLatestAccountBalance("12345678")).thenReturn(Optional.of(
                PaperAccountBalance.builder()
                        .availableCash(new BigDecimal("10000000"))
                        .totalAssetAmount(new BigDecimal("30000000"))
                        .build()
        ));
        when(realizedProfitLossMapper.countRecentLossesByStock(
                eq("12345678"), eq("005930"), eq(true), any()
        )).thenReturn(1);

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
                stockPriceDailyMapper,
                riskGuardProperties,
                orderRequestMapper,
                stockMasterMapper,
                marketContextService,
                realizedProfitLossMapper,
                stockIndicatorDailyMapper
        );

        RiskCheckResult result = service.checkAndSave(12L);

        assertThat(result.getPassed()).isFalse();
        assertThat(result.getFailReason()).contains("동일 종목 최근 실현손실 후 재진입 차단");
    }

    @Test
    @DisplayName("BUY risk check fails when latest RSI is overheated")
    void buyFailsWhenRsiIsOverheated() {
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
        OrderRequestMapper orderRequestMapper = mock(OrderRequestMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);
        MarketContextService marketContextService = mock(MarketContextService.class);
        PortfolioRealizedProfitLossMapper realizedProfitLossMapper = mock(PortfolioRealizedProfitLossMapper.class);
        StockIndicatorDailyMapper stockIndicatorDailyMapper = mock(StockIndicatorDailyMapper.class);

        TradingProperties tradingProperties = new TradingProperties("PAPER", true);
        LiquidityProperties liquidityProperties = new LiquidityProperties(100_000L, 3_000_000_000L);
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
        KisProperties kisProperties = new KisProperties(null, null, "key", "secret", "12345678", "01",
                "/token", "approval");

        AiDecision decision = AiDecision.builder()
                .id(13L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .decision("BUY")
                .confidence(new BigDecimal("0.90"))
                .currentPrice(new BigDecimal("70000"))
                .targetPrice(new BigDecimal("77000"))
                .stopLossPrice(new BigDecimal("66500"))
                .riskRewardRatio(new BigDecimal("2.0"))
                .recommendedPortfolioWeight(new BigDecimal("0.1"))
                .build();

        when(aiDecisionMapper.findById(13L)).thenReturn(Optional.of(decision));
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
                .totalAssetAmount(new BigDecimal("30000000"))
                .build());
        when(paperPortfolioService.findLatestAccountBalance("12345678")).thenReturn(Optional.of(
                PaperAccountBalance.builder()
                        .availableCash(new BigDecimal("10000000"))
                        .totalAssetAmount(new BigDecimal("30000000"))
                        .build()
        ));
        when(stockIndicatorDailyMapper.findLatestByStockCode("005930")).thenReturn(Optional.of(
                StockIndicatorDaily.builder()
                        .stockCode("005930")
                        .rsi14(new BigDecimal("78"))
                        .ma20(new BigDecimal("68000"))
                        .bollingerUpper(new BigDecimal("76000"))
                        .build()
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
                stockPriceDailyMapper,
                riskGuardProperties,
                orderRequestMapper,
                stockMasterMapper,
                marketContextService,
                realizedProfitLossMapper,
                stockIndicatorDailyMapper
        );

        RiskCheckResult result = service.checkAndSave(13L);

        assertThat(result.getPassed()).isFalse();
        assertThat(result.getFailReason()).contains("기술적 과열 BUY 차단");
    }

    @Test
    @DisplayName("BUY risk check fails when expected sector exposure exceeds limit")
    void buyFailsWhenSectorExposureExceedsLimit() {
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
        OrderRequestMapper orderRequestMapper = mock(OrderRequestMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);
        MarketContextService marketContextService = mock(MarketContextService.class);
        PortfolioRealizedProfitLossMapper realizedProfitLossMapper = mock(PortfolioRealizedProfitLossMapper.class);
        StockIndicatorDailyMapper stockIndicatorDailyMapper = mock(StockIndicatorDailyMapper.class);

        TradingProperties tradingProperties = new TradingProperties("PAPER", true);
        LiquidityProperties liquidityProperties = new LiquidityProperties(100_000L, 3_000_000_000L);
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
        KisProperties kisProperties = new KisProperties(null, null, "key", "secret", "12345678", "01",
                "/token", "approval");

        AiDecision decision = AiDecision.builder()
                .id(14L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .decision("BUY")
                .confidence(new BigDecimal("0.90"))
                .currentPrice(new BigDecimal("70000"))
                .targetPrice(new BigDecimal("77000"))
                .stopLossPrice(new BigDecimal("66500"))
                .riskRewardRatio(new BigDecimal("2.0"))
                .recommendedPortfolioWeight(new BigDecimal("0.1"))
                .build();

        when(aiDecisionMapper.findById(14L)).thenReturn(Optional.of(decision));
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
                .totalAssetAmount(new BigDecimal("30000000"))
                .build());
        when(paperPortfolioService.findLatestAccountBalance("12345678")).thenReturn(Optional.of(
                PaperAccountBalance.builder()
                        .availableCash(new BigDecimal("10000000"))
                        .totalAssetAmount(new BigDecimal("30000000"))
                        .build()
        ));
        when(paperPortfolioService.findAllHeldAsPortfolio("12345678")).thenReturn(List.of(
                PortfolioPosition.builder()
                        .accountNo("12345678")
                        .stockCode("000660")
                        .stockName("SK hynix")
                        .quantity(10)
                        .valuationAmount(new BigDecimal("8000000"))
                        .build()
        ));
        when(stockMasterMapper.findByStockCode("005930")).thenReturn(Optional.of(
                StockMaster.builder()
                        .stockCode("005930")
                        .stockName("Samsung Electronics")
                        .sectorName("Semiconductor")
                        .build()
        ));
        when(stockMasterMapper.findByStockCode("000660")).thenReturn(Optional.of(
                StockMaster.builder()
                        .stockCode("000660")
                        .stockName("SK hynix")
                        .sectorName("Semiconductor")
                        .build()
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
                stockPriceDailyMapper,
                riskGuardProperties,
                orderRequestMapper,
                stockMasterMapper,
                marketContextService,
                realizedProfitLossMapper,
                stockIndicatorDailyMapper
        );

        RiskCheckResult result = service.checkAndSave(14L);

        assertThat(result.getPassed()).isFalse();
        assertThat(result.getFailReason()).contains("섹터 집중도 초과 BUY 차단");
    }
}
