package com.bowon.cpm.admin;

import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.market.service.TechnicalIndicatorService;
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

    /**
     * 종목 현재가 조회 + stock_realtime_quote 저장
     * GET /api/stocks/{stockCode}/quote
     */
    @GetMapping("/{stockCode}/quote")
    public ApiResponse<StockQuoteResult> getCurrentPrice(@PathVariable String stockCode) {
        StockQuoteResult result = marketDataService.fetchAndSaveCurrentPrice(stockCode);
        return ApiResponse.ok(result);
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
}
