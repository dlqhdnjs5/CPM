package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.stock.service.StockMarketTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/stocks")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockMarketTypeService stockMarketTypeService;

    @PatchMapping("/{stockCode}/market-type")
    public ApiResponse<StockMarketTypeService.MarketTypeUpdateResult> updateMarketType(
            @PathVariable String stockCode,
            @RequestParam String marketType
    ) {
        return ApiResponse.ok(stockMarketTypeService.updateMarketType(stockCode, marketType));
    }

    @PatchMapping("/market-types")
    public ApiResponse<StockMarketTypeService.BulkMarketTypeUpdateResult> updateMarketTypes(
            @RequestBody Map<String, String> marketTypes
    ) {
        return ApiResponse.ok(stockMarketTypeService.updateMarketTypes(marketTypes));
    }
}
