package com.bowon.cpm.dashboard.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.common.mapper.ExternalApiCallLogMapper;
import com.bowon.cpm.common.mapper.SchedulerExecutionLogMapper;
import com.bowon.cpm.dashboard.domain.DashboardSummary;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    @DisplayName("summary aggregates PAPER balance, decisions, risk failures, and active stocks")
    void summary() {
        PaperPortfolioService paperPortfolioService = mock(PaperPortfolioService.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);
        AiDecisionMapper aiDecisionMapper = mock(AiDecisionMapper.class);
        RiskCheckResultMapper riskCheckResultMapper = mock(RiskCheckResultMapper.class);
        OrderRequestMapper orderRequestMapper = mock(OrderRequestMapper.class);
        SchedulerExecutionLogMapper schedulerMapper = mock(SchedulerExecutionLogMapper.class);
        ExternalApiCallLogMapper externalMapper = mock(ExternalApiCallLogMapper.class);
        BrokerApiLogMapper brokerMapper = mock(BrokerApiLogMapper.class);

        DashboardService service = new DashboardService(
                new TradingProperties("PAPER", false),
                new KisProperties(null, null, "key", "secret", "12345678", "01", "/token", "/approval"),
                paperPortfolioService,
                stockMasterMapper,
                aiDecisionMapper,
                riskCheckResultMapper,
                orderRequestMapper,
                schedulerMapper,
                externalMapper,
                brokerMapper
        );

        when(paperPortfolioService.findLatestAccountBalance("12345678")).thenReturn(Optional.of(
                PaperAccountBalance.builder()
                        .accountNo("12345678")
                        .baseDatetime(LocalDateTime.of(2026, 7, 3, 9, 0))
                        .cashBalance(new BigDecimal("10000000"))
                        .availableCash(new BigDecimal("9000000"))
                        .totalAssetAmount(new BigDecimal("10100000"))
                        .totalEvaluationAmount(new BigDecimal("1100000"))
                        .totalProfitLossAmount(new BigDecimal("100000"))
                        .totalProfitLossRate(new BigDecimal("1.0"))
                        .build()
        ));
        when(paperPortfolioService.findPositions("12345678")).thenReturn(List.of());
        when(stockMasterMapper.findAllActive()).thenReturn(List.of(
                StockMaster.builder().stockCode("005930").stockName("Samsung Electronics").build(),
                StockMaster.builder().stockCode("000660").stockName("SK hynix").build()
        ));
        when(stockMasterMapper.findAll()).thenReturn(List.of(
                StockMaster.builder().stockCode("005930").stockName("Samsung Electronics").isActive(true).build(),
                StockMaster.builder().stockCode("000660").stockName("SK hynix").isActive(true).build(),
                StockMaster.builder().stockCode("009150").stockName("Samsung Electro-Mechanics").isActive(false).build()
        ));
        when(aiDecisionMapper.findRecent(12)).thenReturn(List.of(
                AiDecision.builder().decision("BUY").build(),
                AiDecision.builder().decision("HOLD").build(),
                AiDecision.builder().decision("SELL").build()
        ));
        when(riskCheckResultMapper.findRecent(12)).thenReturn(List.of(
                RiskCheckResult.builder().passed(false).build(),
                RiskCheckResult.builder().passed(true).build()
        ));
        when(orderRequestMapper.findByAccountNo("12345678", "ALL", 12)).thenReturn(List.of());
        when(schedulerMapper.findRecent(12)).thenReturn(List.of());
        when(externalMapper.findRecentFailures(12)).thenReturn(List.of());
        when(brokerMapper.findRecentFailures(12)).thenReturn(List.of());

        DashboardSummary summary = service.summary();

        assertThat(summary.system().tradingMode()).isEqualTo("PAPER");
        assertThat(summary.system().accountNoMasked()).isEqualTo("1234****");
        assertThat(summary.system().activeStockCount()).isEqualTo(2);
        assertThat(summary.activeStocks()).extracting(StockMaster::getStockName)
                .containsExactly("Samsung Electronics", "SK hynix");
        assertThat(summary.stockMasterList()).hasSize(3);
        assertThat(summary.paperBalance().getTotalAssetAmount()).isEqualByComparingTo("10100000");
        assertThat(summary.decisions().buyCount()).isEqualTo(1);
        assertThat(summary.decisions().holdCount()).isEqualTo(1);
        assertThat(summary.decisions().sellCount()).isEqualTo(1);
        assertThat(summary.decisions().failedRiskCount()).isEqualTo(1);
    }
}
