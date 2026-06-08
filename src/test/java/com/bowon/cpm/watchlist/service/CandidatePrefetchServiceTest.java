package com.bowon.cpm.watchlist.service;

import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.dart.service.DartMajorEventService;
import com.bowon.cpm.dart.service.DartService;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.fundamental.service.FundamentalIndicatorService;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.market.service.TechnicalIndicatorService;
import com.bowon.cpm.news.service.NewsAnalysisService;
import com.bowon.cpm.news.service.NewsCollectService;
import com.bowon.cpm.stock.service.StockService;
import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.mapper.StockCandidateScoreMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidatePrefetchServiceTest {

    @Test
    @DisplayName("prefetch runs best-effort steps and analyzes news once")
    void prefetchBestEffort() {
        StockCandidateScoreMapper candidateMapper = mock(StockCandidateScoreMapper.class);
        StockService stockService = mock(StockService.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        TechnicalIndicatorService technicalIndicatorService = mock(TechnicalIndicatorService.class);
        NewsCollectService newsCollectService = mock(NewsCollectService.class);
        NewsAnalysisService newsAnalysisService = mock(NewsAnalysisService.class);
        DartService dartService = mock(DartService.class);
        DartMajorEventService dartMajorEventService = mock(DartMajorEventService.class);
        DartFinancialService dartFinancialService = mock(DartFinancialService.class);
        FundamentalIndicatorService fundamentalIndicatorService = mock(FundamentalIndicatorService.class);

        when(candidateMapper.findPrefetchTargets(2)).thenReturn(List.of(
                StockCandidateMetrics.builder()
                        .stockCode("000001")
                        .stockName("One")
                        .corpCode("corp1")
                        .build(),
                StockCandidateMetrics.builder()
                        .stockCode("000002")
                        .stockName("Two")
                        .corpCode("corp2")
                        .build()
        ));
        when(marketDataService.fetchAndSaveDailyPrices("000001")).thenReturn(30);
        when(marketDataService.fetchAndSaveDailyPrices("000002")).thenThrow(new RuntimeException("KIS failed"));
        when(technicalIndicatorService.calculateForStock(any())).thenReturn(StockIndicatorDaily.builder().build());
        when(newsCollectService.collectNews(any(), any(), eq(10))).thenReturn(3);
        when(dartService.fetchDisclosures(any(), any(), any())).thenReturn(1);
        when(dartMajorEventService.classifyAndSave(any())).thenReturn(1);
        when(dartFinancialService.fetchAndSave(any())).thenReturn(1);
        when(dartFinancialService.fetchAndSaveStockQuantity(any())).thenReturn(1);
        when(fundamentalIndicatorService.calculateAndSave(any())).thenReturn(StockFundamentalIndicator.builder().build());
        when(newsAnalysisService.analyzePending(5)).thenReturn(new NewsAnalysisService.AnalysisBatchResult(5, 4, 1));

        CandidatePrefetchService service = new CandidatePrefetchService(
                candidateMapper,
                stockService,
                marketDataService,
                technicalIndicatorService,
                newsCollectService,
                newsAnalysisService,
                dartService,
                dartMajorEventService,
                dartFinancialService,
                fundamentalIndicatorService
        );

        CandidatePrefetchService.PrefetchBatchResult result = service.prefetch(2, 5);

        assertThat(result.targetCount()).isEqualTo(2);
        assertThat(result.succeededCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.analyzedNewsCount()).isEqualTo(4);
        verify(stockService).upsertStockMasterFromDart("000001", "One", "corp1");
        verify(stockService).upsertStockMasterFromDart("000002", "Two", "corp2");
        verify(newsAnalysisService).analyzePending(5);
    }
}
