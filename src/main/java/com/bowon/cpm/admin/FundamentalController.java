package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.dart.domain.DartStockQuantity;
import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.fundamental.service.FundamentalIndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FundamentalController {

    private final DartFinancialService dartFinancialService;
    private final FundamentalIndicatorService fundamentalIndicatorService;

    @PostMapping("/dart/{stockCode}/stock-quantity/fetch")
    public ApiResponse<Map<String, Object>> fetchStockQuantity(@PathVariable String stockCode) {
        int count = dartFinancialService.fetchAndSaveStockQuantity(stockCode);
        return ApiResponse.ok("DART stock quantity fetched", Map.of(
                "stockCode", stockCode,
                "savedCount", count
        ));
    }

    @GetMapping("/dart/{stockCode}/stock-quantity")
    public ApiResponse<List<DartStockQuantity>> getStockQuantity(@PathVariable String stockCode) {
        return ApiResponse.ok(dartFinancialService.getStockQuantities(stockCode));
    }

    @PostMapping("/stocks/{stockCode}/fundamentals/calculate")
    public ApiResponse<StockFundamentalIndicator> calculateFundamentals(@PathVariable String stockCode) {
        return ApiResponse.ok("Fundamentals calculated", fundamentalIndicatorService.calculateAndSave(stockCode));
    }

    @GetMapping("/stocks/{stockCode}/fundamentals")
    public ApiResponse<List<StockFundamentalIndicator>> getFundamentals(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(fundamentalIndicatorService.findByStockCode(stockCode, limit));
    }
}
