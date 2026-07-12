package com.bowon.cpm.feedback.service;

import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.feedback.mapper.PortfolioRealizedProfitLossMapper;
import com.bowon.cpm.paper.domain.PaperPortfolioProfitLoss;
import com.bowon.cpm.paper.mapper.PaperPortfolioProfitLossMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerformanceQueryServiceTest {

    @Test
    @DisplayName("Paper performance reads daily P/L from PAPER table")
    void paperPerformanceReadsPaperProfitLoss() {
        PaperPortfolioProfitLossMapper paperProfitLossMapper = mock(PaperPortfolioProfitLossMapper.class);
        PortfolioRealizedProfitLossMapper realizedMapper = mock(PortfolioRealizedProfitLossMapper.class);
        KisProperties kisProperties = new KisProperties(
                "", "", "", "", "ACC", "01", "", ""
        );
        PerformanceQueryService service = new PerformanceQueryService(
                kisProperties,
                paperProfitLossMapper,
                realizedMapper
        );

        when(realizedMapper.aggregatePaperByAccountNo("ACC")).thenReturn(Map.of("sell_count", 0));
        when(realizedMapper.findRecentPaperByAccountNo("ACC", 5)).thenReturn(List.of());
        when(paperProfitLossMapper.findRecentByAccountNo("ACC", 5)).thenReturn(List.of(
                PaperPortfolioProfitLoss.builder()
                        .accountNo("ACC")
                        .stockCode("")
                        .baseDate(LocalDate.of(2026, 6, 5))
                        .evaluationType("DAILY")
                        .returnRate(new BigDecimal("1.2500"))
                        .build()
        ));

        PerformanceQueryService.PaperPerformance performance = service.getPaperPerformance(5);

        assertThat(performance.recentDailyProfitLoss()).hasSize(1);
        assertThat(performance.recentDailyProfitLoss().get(0).getReturnRate())
                .isEqualByComparingTo(new BigDecimal("1.2500"));
        verify(paperProfitLossMapper).findRecentByAccountNo("ACC", 5);
    }
}
