package com.bowon.cpm.watchlist.service;

import com.bowon.cpm.admin.service.StockDataBootstrapService;
import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.WatchlistProperties;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import com.bowon.cpm.stock.service.StockService;
import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.mapper.StockCandidateScoreMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistDiscoveryServiceTest {

    @Test
    @DisplayName("fallback selection respects max daily additions when OpenAI fails")
    void fallbackRespectsMaxDailyAdditions() {
        WatchlistProperties properties = new WatchlistProperties(20, 2, 3, 10, 3);
        KisProperties kisProperties = new KisProperties(null, null, "key", "secret", "12345678", "01",
                "/token", "approval");
        StockCandidateScoreMapper candidateMapper = mock(StockCandidateScoreMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);
        StockService stockService = mock(StockService.class);
        StockDataBootstrapService bootstrapService = mock(StockDataBootstrapService.class);
        CandidatePrefetchService candidatePrefetchService = mock(CandidatePrefetchService.class);
        OpenAiDecisionClient openAiDecisionClient = mock(OpenAiDecisionClient.class);
        OpenAiProperties openAiProperties = new OpenAiProperties(null, "key", "decision", "summary", "review");

        when(stockMasterMapper.countWatched()).thenReturn(18, 18, 20, 20);
        when(candidatePrefetchService.prefetch(3, 0))
                .thenReturn(new CandidatePrefetchService.PrefetchBatchResult(3, 3, 0, 0, List.of()));
        when(candidateMapper.findCandidateMetrics(anyInt())).thenReturn(List.of(
                metric("000001", "One", 10_000_000_000L),
                metric("000002", "Two", 8_000_000_000L),
                metric("000003", "Three", 6_000_000_000L)
        ));
        when(openAiDecisionClient.createTextCompletion(any(), any(), any()))
                .thenThrow(new RuntimeException("OpenAI unavailable"));

        WatchlistDiscoveryService service = new WatchlistDiscoveryService(
                properties,
                kisProperties,
                candidateMapper,
                stockMasterMapper,
                stockService,
                bootstrapService,
                candidatePrefetchService,
                new WatchlistScoringEngine(),
                openAiDecisionClient,
                openAiProperties,
                new ObjectMapper()
        );

        WatchlistDiscoveryService.DiscoveryResult result = service.discover(false, 0);

        assertThat(result.selectedStocks()).hasSize(2);
        assertThat(result.prefetchedCount()).isEqualTo(3);
        assertThat(result.selectedStocks())
                .allMatch(stock -> "FALLBACK_SELECTED".equals(stock.selectionSource()));
        verify(candidatePrefetchService).prefetch(3, 0);
        verify(candidateMapper, times(3)).upsert(any());
        verify(stockService, times(2)).updateWatched(any(), org.mockito.ArgumentMatchers.eq(true));
    }

    private StockCandidateMetrics metric(String stockCode, String stockName, long tradingValue) {
        return StockCandidateMetrics.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .corpCode("corp" + stockCode)
                .closePrice(BigDecimal.valueOf(10000))
                .previousClosePrice(BigDecimal.valueOf(9900))
                .tradingValue(BigDecimal.valueOf(tradingValue))
                .ma5(BigDecimal.valueOf(10100))
                .ma20(BigDecimal.valueOf(9800))
                .ma60(BigDecimal.valueOf(9400))
                .rsi14(BigDecimal.valueOf(60))
                .volumeChangeRate(BigDecimal.valueOf(0.2))
                .averageSentimentScore(BigDecimal.valueOf(0.5))
                .averageImpactScore(BigDecimal.valueOf(0.5))
                .recentNewsCount(3)
                .recentMajorEventCount(0)
                .financialStatementCount(8)
                .build();
    }
}
