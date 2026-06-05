package com.bowon.cpm.admin.service;

import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.dart.service.DartMajorEventService;
import com.bowon.cpm.dart.service.DartService;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.market.service.TechnicalIndicatorService;
import com.bowon.cpm.news.service.NewsAnalysisService;
import com.bowon.cpm.news.service.NewsCollectService;
import com.bowon.cpm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class StockDataBootstrapService {

    private final DartCorpCodeMapper dartCorpCodeMapper;
    private final StockService stockService;
    private final DartService dartService;
    private final DartMajorEventService dartMajorEventService;
    private final DartFinancialService dartFinancialService;
    private final NewsCollectService newsCollectService;
    private final NewsAnalysisService newsAnalysisService;
    private final MarketDataService marketDataService;
    private final TechnicalIndicatorService technicalIndicatorService;

    public BootstrapResult bootstrap(String inputCode, LocalDate from, LocalDate to, String keyword, int newsDisplay, int newsAnalyzeLimit) {
        LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusMonths(3);
        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        List<BootstrapStep> steps = new ArrayList<>();

        DartCorpCode corpCode = resolveCorpCode(inputCode);
        String resolvedStockCode = corpCode.getStockCode();
        if (resolvedStockCode == null || resolvedStockCode.isBlank()) {
            throw new IllegalArgumentException("Listed stock_code not found for corpCode=" + corpCode.getCorpCode());
        }

        String stockName = hasText(corpCode.getCorpName()) ? corpCode.getCorpName() : resolvedStockCode;
        String resolvedKeyword = hasText(keyword) ? keyword : stockName;

        steps.add(run("stockMasterUpsert", () -> {
            stockService.upsertStockMasterFromDart(resolvedStockCode, stockName, corpCode.getCorpCode());
            return 1;
        }));
        steps.add(run("dartDisclosuresFetch", () -> dartService.fetchDisclosures(resolvedStockCode, resolvedFrom, resolvedTo)));
        steps.add(run("dartMajorEventsClassify", () -> dartMajorEventService.classifyAndSave(resolvedStockCode)));
        steps.add(run("dartFinancialsFetch", () -> dartFinancialService.fetchAndSave(resolvedStockCode)));
        steps.add(run("newsFetch", () -> newsCollectService.collectNews(resolvedStockCode, resolvedKeyword, newsDisplay)));
        steps.add(run("newsAnalyze", () -> {
            NewsAnalysisService.AnalysisBatchResult result = newsAnalysisService.analyzePending(newsAnalyzeLimit);
            return result.saved();
        }));
        steps.add(run("currentQuoteFetch", () -> {
            StockQuoteResult quote = marketDataService.fetchAndSaveCurrentPrice(resolvedStockCode);
            return quote != null ? 1 : 0;
        }));
        steps.add(run("dailyPricesFetch", () -> marketDataService.fetchAndSaveDailyPrices(resolvedStockCode)));
        steps.add(run("dailyIndicatorsCalculate", () -> {
            StockIndicatorDaily indicator = technicalIndicatorService.calculateForStock(resolvedStockCode);
            return indicator != null ? 1 : 0;
        }));

        boolean allSucceeded = steps.stream().allMatch(BootstrapStep::success);
        return new BootstrapResult(
                inputCode,
                resolvedStockCode,
                stockName,
                corpCode.getCorpCode(),
                resolvedKeyword,
                resolvedFrom,
                resolvedTo,
                allSucceeded,
                steps
        );
    }

    private DartCorpCode resolveCorpCode(String inputCode) {
        return dartCorpCodeMapper.findByStockCode(inputCode)
                .or(() -> dartCorpCodeMapper.findByCorpCode(inputCode))
                .orElseThrow(() -> new IllegalArgumentException(
                        "corp_code not found: inputCode=" + inputCode + ", run /api/dart/corp-codes/sync first"));
    }

    private BootstrapStep run(String stepName, Supplier<Integer> action) {
        long start = System.currentTimeMillis();
        try {
            Integer count = action.get();
            return new BootstrapStep(stepName, true, count != null ? count : 0, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new BootstrapStep(stepName, false, 0, e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record BootstrapResult(
            String inputCode,
            String stockCode,
            String stockName,
            String corpCode,
            String keyword,
            LocalDate from,
            LocalDate to,
            boolean allSucceeded,
            List<BootstrapStep> steps
    ) {
    }

    public record BootstrapStep(
            String stepName,
            boolean success,
            int count,
            String errorMessage,
            long elapsedMs
    ) {
    }
}
