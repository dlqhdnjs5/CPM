package com.bowon.cpm.admin;

import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.admin.service.StockDataBootstrapService;
import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.domain.StockSupplyDemandDaily;
import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.market.service.SupplyDemandService;
import com.bowon.cpm.market.service.TechnicalIndicatorService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class MarketController {

    private final MarketDataService marketDataService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final StockDataBootstrapService stockDataBootstrapService;
    private final SupplyDemandService supplyDemandService;
    private final StockMasterMapper stockMasterMapper;
    private final DartCorpCodeMapper dartCorpCodeMapper;

    @GetMapping("/candidates")
    public ApiResponse<List<StockCandidateResponse>> searchStockCandidates(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        String keyword = required(query, "query");
        int resolvedLimit = Math.max(1, Math.min(limit, 20));
        List<StockCandidateResponse> candidates = dartCorpCodeMapper
                .searchInactiveListedCandidates(keyword, resolvedLimit)
                .stream()
                .map(StockCandidateResponse::from)
                .toList();
        return ApiResponse.ok(candidates);
    }

    @PostMapping("/active")
    public ApiResponse<ActiveStockBootstrapResponse> addActiveStock(@RequestBody ActiveStockRequest request) {
        String stockCode = required(request.stockCode(), "stockCode");
        DartCorpCode candidate = dartCorpCodeMapper.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("DART listed stock not found: " + stockCode));
        if (candidate.getStockCode() == null || candidate.getStockCode().isBlank()) {
            throw new IllegalArgumentException("DART candidate is not a listed stock: " + stockCode);
        }

        StockDataBootstrapService.BootstrapResult bootstrap = stockDataBootstrapService.bootstrap(
                stockCode,
                null,
                null,
                null,
                request.newsDisplay() != null ? request.newsDisplay() : 30,
                request.newsAnalyzeLimit() != null ? request.newsAnalyzeLimit() : 20
        );
        StockMaster saved = stockMasterMapper.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalStateException("stock_master not found after bootstrap: " + stockCode));
        return ApiResponse.ok("active stock added and bootstrap completed",
                new ActiveStockBootstrapResponse(ActiveStockResponse.from(saved), bootstrap));
    }

    @PatchMapping("/{stockCode}/active")
    public ApiResponse<ActiveStockResponse> updateActiveStock(
            @PathVariable String stockCode,
            @RequestBody ActiveToggleRequest request
    ) {
        StockMaster existing = stockMasterMapper.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("stock_master not found: " + stockCode));
        boolean active = request.active();
        stockMasterMapper.updateActive(existing.getStockCode(), active);
        StockMaster saved = stockMasterMapper.findByStockCode(existing.getStockCode()).orElse(existing);
        return ApiResponse.ok("active flag updated", ActiveStockResponse.from(saved));
    }

    /**
     * 종목 현재가 조회 + stock_realtime_quote 저장
     * GET /api/stocks/{stockCode}/quote
     */
    @GetMapping("/{stockCode}/quote")
    public ApiResponse<StockQuoteResult> getCurrentPrice(@PathVariable String stockCode) {
        StockQuoteResult result = marketDataService.fetchAndSaveCurrentPrice(stockCode);
        return ApiResponse.ok(result);
    }

    @PostMapping("/{stockCode}/ai-data/bootstrap")
    public ApiResponse<StockDataBootstrapService.BootstrapResult> bootstrapAiData(
            @PathVariable String stockCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") int newsDisplay,
            @RequestParam(defaultValue = "50") int newsAnalyzeLimit
    ) {
        StockDataBootstrapService.BootstrapResult result = stockDataBootstrapService.bootstrap(
                stockCode, from, to, keyword, newsDisplay, newsAnalyzeLimit);
        return ApiResponse.ok("AI data bootstrap completed", result);
    }

    /**
     * 종목 일봉 조회 + stock_price_daily 저장 (KIS에서 최근 30영업일 수집)
     * POST /api/stocks/{stockCode}/prices/daily/fetch
     */
    @PostMapping("/{stockCode}/prices/daily/fetch")
    public ApiResponse<Map<String, Object>> fetchDailyPrices(@PathVariable String stockCode) {
        int savedCount = marketDataService.fetchAndSaveDailyPrices(stockCode);
        return ApiResponse.ok("일봉 수집 완료", Map.of(
                "stockCode", stockCode,
                "savedCount", savedCount
        ));
    }

    /**
     * 저장된 일봉 조회 (DB에서 조회)
     * GET /api/stocks/{stockCode}/prices/daily?from=2026-01-01&to=2026-05-31
     */
    @GetMapping("/{stockCode}/prices/daily")
    public ApiResponse<List<StockPriceDaily>> getDailyPrices(
            @PathVariable String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<StockPriceDaily> prices = marketDataService.getDailyPrices(stockCode, from, to);
        return ApiResponse.ok(prices);
    }

    @PostMapping("/{stockCode}/supply-demand/fetch")
    public ApiResponse<Map<String, Object>> fetchSupplyDemand(@PathVariable String stockCode) {
        int savedCount = supplyDemandService.fetchAndSave(stockCode);
        return ApiResponse.ok("supply demand fetch completed", Map.of(
                "stockCode", stockCode,
                "savedCount", savedCount
        ));
    }

    @GetMapping("/{stockCode}/supply-demand")
    public ApiResponse<List<StockSupplyDemandDaily>> getSupplyDemand(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.ok(supplyDemandService.getRecent(stockCode, limit));
    }

    @PostMapping("/{stockCode}/indicators/daily/calculate")
    public ApiResponse<Map<String, Object>> calculateDailyIndicator(@PathVariable String stockCode) {
        StockIndicatorDaily indicator = technicalIndicatorService.calculateForStock(stockCode);
        return ApiResponse.ok("기술지표 계산 완료", Map.of(
                "stockCode", stockCode,
                "saved", indicator != null,
                "tradeDate", indicator != null && indicator.getTradeDate() != null
                        ? indicator.getTradeDate().toString() : "N/A"
        ));
    }

    @PostMapping("/indicators/daily/calculate")
    public ApiResponse<Map<String, Object>> calculateAllDailyIndicators() {
        int saved = technicalIndicatorService.calculateAllActive();
        return ApiResponse.ok("전체 기술지표 계산 완료", Map.of("savedCount", saved));
    }
    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ActiveStockRequest(
            String stockCode,
            Integer newsDisplay,
            Integer newsAnalyzeLimit
    ) {
    }

    public record ActiveToggleRequest(boolean active) {
    }

    public record StockCandidateResponse(
            String stockCode,
            String stockName,
            String corpCode
    ) {
        static StockCandidateResponse from(DartCorpCode corpCode) {
            return new StockCandidateResponse(
                    corpCode.getStockCode(),
                    corpCode.getCorpName(),
                    corpCode.getCorpCode()
            );
        }
    }

    public record ActiveStockResponse(
            String stockCode,
            String stockName,
            String marketType,
            boolean active
    ) {
        static ActiveStockResponse from(StockMaster stock) {
            return new ActiveStockResponse(
                    stock.getStockCode(),
                    stock.getStockName(),
                    stock.getMarketType(),
                    Boolean.TRUE.equals(stock.getIsActive())
            );
        }
    }

    public record ActiveStockBootstrapResponse(
            ActiveStockResponse stock,
            StockDataBootstrapService.BootstrapResult bootstrap
    ) {
    }
}
