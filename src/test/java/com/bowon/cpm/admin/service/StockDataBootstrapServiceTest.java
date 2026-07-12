package com.bowon.cpm.admin.service;

import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDataBootstrapServiceTest {

    @Mock DartCorpCodeMapper dartCorpCodeMapper;
    @Mock StockService stockService;
    @Mock DartService dartService;
    @Mock DartMajorEventService dartMajorEventService;
    @Mock DartFinancialService dartFinancialService;
    @Mock NewsCollectService newsCollectService;
    @Mock NewsAnalysisService newsAnalysisService;
    @Mock MarketDataService marketDataService;
    @Mock TechnicalIndicatorService technicalIndicatorService;
    @Mock FundamentalIndicatorService fundamentalIndicatorService;

    StockDataBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new StockDataBootstrapService(
                dartCorpCodeMapper,
                stockService,
                dartService,
                dartMajorEventService,
                dartFinancialService,
                newsCollectService,
                newsAnalysisService,
                marketDataService,
                technicalIndicatorService,
                fundamentalIndicatorService
        );
    }

    @Test
    @DisplayName("Bootstraps all AI input data for a stock code")
    void bootstrapAllSteps() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 6, 5);
        when(dartCorpCodeMapper.findByStockCode("042700")).thenReturn(Optional.of(corpCode()));
        when(dartService.fetchDisclosures("042700", from, to)).thenReturn(22);
        when(dartMajorEventService.classifyAndSave("042700")).thenReturn(1);
        when(dartFinancialService.fetchAndSave("042700")).thenReturn(10);
        when(dartFinancialService.fetchAndSaveStockQuantity("042700")).thenReturn(2);
        when(newsCollectService.collectNews("042700", "한미반도체", 30)).thenReturn(30);
        when(newsAnalysisService.analyzePending(50)).thenReturn(new NewsAnalysisService.AnalysisBatchResult(30, 30, 0));
        when(marketDataService.fetchAndSaveCurrentPrice("042700")).thenReturn(StockQuoteResult.builder()
                .stockCode("042700")
                .currentPrice(new BigDecimal("291500"))
                .build());
        when(marketDataService.fetchAndSaveDailyPrices("042700")).thenReturn(30);
        when(technicalIndicatorService.calculateForStock("042700")).thenReturn(StockIndicatorDaily.builder()
                .stockCode("042700")
                .tradeDate(to)
                .build());
        when(fundamentalIndicatorService.calculateAndSave("042700"))
                .thenReturn(StockFundamentalIndicator.builder().totalScore(new BigDecimal("12.5")).build());

        StockDataBootstrapService.BootstrapResult result = service.bootstrap("042700", from, to, null, 30, 50);

        assertThat(result.allSucceeded()).isTrue();
        assertThat(result.stockCode()).isEqualTo("042700");
        assertThat(result.stockName()).isEqualTo("한미반도체");
        assertThat(result.steps()).hasSize(11);
        verify(stockService).upsertStockMasterFromDart("042700", "한미반도체", "00161383");
    }

    @Test
    @DisplayName("Accepts DART corp code and continues when one step fails")
    void acceptsCorpCodeAndRecordsStepFailure() {
        when(dartCorpCodeMapper.findByStockCode("00161383")).thenReturn(Optional.empty());
        when(dartCorpCodeMapper.findByCorpCode("00161383")).thenReturn(Optional.of(corpCode()));
        when(dartService.fetchDisclosures("042700", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 5)))
                .thenThrow(new RuntimeException("DART failed"));

        StockDataBootstrapService.BootstrapResult result = service.bootstrap(
                "00161383", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 5), "AI 반도체", 10, 5);

        assertThat(result.allSucceeded()).isFalse();
        assertThat(result.stockCode()).isEqualTo("042700");
        assertThat(result.keyword()).isEqualTo("AI 반도체");
        assertThat(result.steps())
                .anySatisfy(step -> {
                    assertThat(step.stepName()).isEqualTo("dartDisclosuresFetch");
                    assertThat(step.success()).isFalse();
                    assertThat(step.errorMessage()).contains("DART failed");
                });
    }

    private DartCorpCode corpCode() {
        return DartCorpCode.builder()
                .corpCode("00161383")
                .stockCode("042700")
                .corpName("한미반도체")
                .build();
    }
}
