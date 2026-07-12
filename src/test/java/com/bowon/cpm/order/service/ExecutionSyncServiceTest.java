package com.bowon.cpm.order.service;

import com.bowon.cpm.broker.kis.KisExecutionClient;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.feedback.service.RealizedProfitLossService;
import com.bowon.cpm.order.mapper.OrderExecutionMapper;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.mapper.OrderStatusHistoryMapper;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.portfolio.service.PortfolioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionSyncServiceTest {

    @Mock KisExecutionClient kisExecutionClient;
    @Mock OrderExecutionMapper orderExecutionMapper;
    @Mock OrderRequestMapper orderRequestMapper;
    @Mock OrderStatusHistoryMapper orderStatusHistoryMapper;
    @Mock PortfolioPositionMapper portfolioPositionMapper;
    @Mock PortfolioService portfolioService;
    @Mock RealizedProfitLossService realizedProfitLossService;
    @Mock BrokerApiLogMapper brokerApiLogMapper;
    @Mock TradingProperties tradingProperties;

    @Test
    @DisplayName("Skips KIS execution sync in PAPER mode")
    void skipsKisSyncInPaperMode() {
        when(tradingProperties.isPaperMode()).thenReturn(true);
        ExecutionSyncService service = new ExecutionSyncService(
                kisExecutionClient,
                orderExecutionMapper,
                orderRequestMapper,
                orderStatusHistoryMapper,
                portfolioPositionMapper,
                portfolioService,
                realizedProfitLossService,
                brokerApiLogMapper,
                tradingProperties
        );

        int saved = service.syncExecutions();

        assertThat(saved).isZero();
        verifyNoInteractions(kisExecutionClient);
    }
}
