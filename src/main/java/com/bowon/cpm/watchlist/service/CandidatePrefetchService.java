package com.bowon.cpm.watchlist.service;

import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.dart.service.DartMajorEventService;
import com.bowon.cpm.dart.service.DartService;
import com.bowon.cpm.fundamental.service.FundamentalIndicatorService;
import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.market.service.TechnicalIndicatorService;
import com.bowon.cpm.news.service.NewsAnalysisService;
import com.bowon.cpm.news.service.NewsCollectService;
import com.bowon.cpm.stock.service.StockService;
import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.mapper.StockCandidateScoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidatePrefetchService {

    private static final int NEWS_DISPLAY = 10;

    private final StockCandidateScoreMapper candidateScoreMapper;
    private final StockService stockService;
    private final MarketDataService marketDataService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final NewsCollectService newsCollectService;
    private final NewsAnalysisService newsAnalysisService;
    private final DartService dartService;
    private final DartMajorEventService dartMajorEventService;
    private final DartFinancialService dartFinancialService;
    private final FundamentalIndicatorService fundamentalIndicatorService;

    public PrefetchBatchResult prefetch(int limit, int newsAnalyzeLimit) {
        int normalizedLimit = Math.max(0, limit);
        if (normalizedLimit == 0) {
            return new PrefetchBatchResult(0, 0, 0, 0, List.of());
        }

        List<StockCandidateMetrics> targets = candidateScoreMapper.findPrefetchTargets(normalizedLimit);
        List<PrefetchedStock> results = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (StockCandidateMetrics target : targets) {
            PrefetchedStock result = prefetchOne(target);
            results.add(result);
            if (result.success()) {
                succeeded++;
            } else {
                failed++;
            }
        }

        int analyzedNews = analyzeNews(newsAnalyzeLimit);
        return new PrefetchBatchResult(targets.size(), succeeded, failed, analyzedNews, results);
    }

    private PrefetchedStock prefetchOne(StockCandidateMetrics target) {
        List<PrefetchStep> steps = new ArrayList<>();
        String stockCode = target.getStockCode();
        String stockName = hasText(target.getStockName()) ? target.getStockName() : stockCode;
        String corpCode = target.getCorpCode();

        steps.add(run("stockMasterUpsert", () -> {
            if (hasText(corpCode)) {
                stockService.upsertStockMasterFromDart(stockCode, stockName, corpCode);
            } else {
                stockService.upsertStockMaster(stockCode, stockName, "UNKNOWN");
            }
            return 1;
        }));
        steps.add(run("dailyPricesFetch", () -> marketDataService.fetchAndSaveDailyPrices(stockCode)));
        steps.add(run("dailyIndicatorsCalculate", () -> technicalIndicatorService.calculateForStock(stockCode) != null ? 1 : 0));
        steps.add(run("newsFetch", () -> newsCollectService.collectNews(stockCode, stockName, NEWS_DISPLAY)));
        steps.add(run("dartDisclosuresFetch", () -> dartService.fetchDisclosures(stockCode, LocalDate.now().minusDays(30), LocalDate.now())));
        steps.add(run("dartMajorEventsClassify", () -> dartMajorEventService.classifyAndSave(stockCode)));
        steps.add(run("dartFinancialsFetch", () -> dartFinancialService.fetchAndSave(stockCode)));
        steps.add(run("dartStockQuantityFetch", () -> dartFinancialService.fetchAndSaveStockQuantity(stockCode)));
        steps.add(run("fundamentalIndicatorsCalculate", () -> fundamentalIndicatorService.calculateAndSave(stockCode) != null ? 1 : 0));

        boolean success = steps.stream()
                .filter(step -> !"newsFetch".equals(step.stepName()))
                .filter(step -> !"dartDisclosuresFetch".equals(step.stepName()))
                .filter(step -> !"dartMajorEventsClassify".equals(step.stepName()))
                .filter(step -> !"dartFinancialsFetch".equals(step.stepName()))
                .filter(step -> !"dartStockQuantityFetch".equals(step.stepName()))
                .filter(step -> !"fundamentalIndicatorsCalculate".equals(step.stepName()))
                .allMatch(PrefetchStep::success);

        return new PrefetchedStock(stockCode, stockName, success, steps);
    }

    private int analyzeNews(int newsAnalyzeLimit) {
        if (newsAnalyzeLimit <= 0) {
            return 0;
        }
        try {
            NewsAnalysisService.AnalysisBatchResult result = newsAnalysisService.analyzePending(newsAnalyzeLimit);
            return result.saved();
        } catch (Exception e) {
            log.warn("[WatchlistPrefetch] news analysis failed: {}", e.getMessage());
            return 0;
        }
    }

    private PrefetchStep run(String stepName, Supplier<Integer> action) {
        long start = System.currentTimeMillis();
        try {
            Integer count = action.get();
            return new PrefetchStep(stepName, true, count != null ? count : 0, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("[WatchlistPrefetch] step failed: step={}, message={}", stepName, e.getMessage());
            return new PrefetchStep(stepName, false, 0, e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PrefetchBatchResult(
            int targetCount,
            int succeededCount,
            int failedCount,
            int analyzedNewsCount,
            List<PrefetchedStock> stocks
    ) {
    }

    public record PrefetchedStock(
            String stockCode,
            String stockName,
            boolean success,
            List<PrefetchStep> steps
    ) {
    }

    public record PrefetchStep(
            String stepName,
            boolean success,
            int count,
            String errorMessage,
            long elapsedMs
    ) {
    }
}
