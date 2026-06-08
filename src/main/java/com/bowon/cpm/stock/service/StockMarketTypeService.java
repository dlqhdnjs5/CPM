package com.bowon.cpm.stock.service;

import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockMarketTypeService {

    private final StockMasterMapper stockMasterMapper;

    @Transactional
    public MarketTypeUpdateResult updateMarketType(String stockCode, String marketType) {
        String normalizedStockCode = normalizeStockCode(stockCode);
        String normalizedMarketType = normalizeMarketType(marketType);
        int updated = stockMasterMapper.updateMarketType(normalizedStockCode, normalizedMarketType);
        return new MarketTypeUpdateResult(normalizedStockCode, normalizedMarketType, updated == 1);
    }

    @Transactional
    public BulkMarketTypeUpdateResult updateMarketTypes(Map<String, String> marketTypes) {
        if (marketTypes == null || marketTypes.isEmpty()) {
            return new BulkMarketTypeUpdateResult(0, 0, Map.of());
        }

        Map<String, MarketTypeUpdateResult> results = new LinkedHashMap<>();
        int updatedCount = 0;
        for (Map.Entry<String, String> entry : marketTypes.entrySet()) {
            MarketTypeUpdateResult result = updateMarketType(entry.getKey(), entry.getValue());
            results.put(result.stockCode(), result);
            if (result.updated()) {
                updatedCount++;
            }
        }
        return new BulkMarketTypeUpdateResult(marketTypes.size(), updatedCount, results);
    }

    String normalizeMarketType(String marketType) {
        if (marketType == null || marketType.isBlank()) {
            throw new IllegalArgumentException("marketType is required");
        }
        String normalized = marketType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "KOSPI", "KOSDAQ", "UNKNOWN" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported marketType: " + marketType);
        };
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        return stockCode.trim();
    }

    public record MarketTypeUpdateResult(
            String stockCode,
            String marketType,
            boolean updated
    ) {
    }

    public record BulkMarketTypeUpdateResult(
            int requestedCount,
            int updatedCount,
            Map<String, MarketTypeUpdateResult> results
    ) {
    }
}
