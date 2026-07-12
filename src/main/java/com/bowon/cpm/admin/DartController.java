package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartFinancialStatement;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.dart.service.DartMajorEventService;
import com.bowon.cpm.dart.service.DartService;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.fundamental.service.FundamentalIndicatorService;
import com.bowon.cpm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DartController {

    private final DartService dartService;
    private final DartFinancialService dartFinancialService;
    private final DartMajorEventService dartMajorEventService;
    private final DartCorpCodeMapper dartCorpCodeMapper;
    private final StockService stockService;
    private final FundamentalIndicatorService fundamentalIndicatorService;

    @PostMapping("/dart/corp-codes/sync")
    public ApiResponse<Map<String, Object>> syncCorpCodes() {
        int count = dartService.syncCorpCodes();
        return ApiResponse.ok("corp codes synced", Map.of("savedCount", count));
    }

    @PostMapping("/dart/{stockCode}/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrapStockDartData(
            @PathVariable String stockCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusMonths(3);
        LocalDate resolvedTo = to != null ? to : LocalDate.now();

        DartCorpCode corpCode = dartCorpCodeMapper.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "corp_code not found: stockCode=" + stockCode + ", run /api/dart/corp-codes/sync first"));

        String stockName = corpCode.getCorpName() != null && !corpCode.getCorpName().isBlank()
                ? corpCode.getCorpName()
                : stockCode;
        stockService.upsertStockMasterFromDart(stockCode, stockName, corpCode.getCorpCode());

        int disclosureCount = dartService.fetchDisclosures(stockCode, resolvedFrom, resolvedTo);
        int eventCount = dartMajorEventService.classifyAndSave(stockCode);
        int financialCount = dartFinancialService.fetchAndSave(stockCode);
        int stockQuantityCount = dartFinancialService.fetchAndSaveStockQuantity(stockCode);

        StockFundamentalIndicator indicator = null;
        String fundamentalError = null;
        try {
            indicator = fundamentalIndicatorService.calculateAndSave(stockCode);
        } catch (Exception e) {
            fundamentalError = e.getMessage();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stockCode", stockCode);
        response.put("stockName", stockName);
        response.put("corpCode", corpCode.getCorpCode());
        response.put("from", resolvedFrom);
        response.put("to", resolvedTo);
        response.put("disclosureCount", disclosureCount);
        response.put("eventCount", eventCount);
        response.put("financialCount", financialCount);
        response.put("stockQuantityCount", stockQuantityCount);
        response.put("fundamentalCalculated", indicator != null);
        if (indicator != null) {
            response.put("fundamentalTotalScore", indicator.getTotalScore());
        }
        if (fundamentalError != null) {
            response.put("fundamentalError", fundamentalError);
        }

        return ApiResponse.ok("DART bootstrap completed", response);
    }

    @PostMapping("/dart/{stockCode}/disclosures/fetch")
    public ApiResponse<Map<String, Object>> fetchDisclosures(
            @PathVariable String stockCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusMonths(3);
        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        int count = dartService.fetchDisclosures(stockCode, resolvedFrom, resolvedTo);
        return ApiResponse.ok("disclosures fetched", Map.of("stockCode", stockCode, "savedCount", count));
    }

    @GetMapping("/stocks/{stockCode}/disclosures")
    public ApiResponse<List<DartDisclosure>> getDisclosures(
            @PathVariable String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.ok(dartService.getDisclosures(stockCode, from, to));
    }

    @PostMapping("/dart/{stockCode}/financials/fetch")
    public ApiResponse<Map<String, Object>> fetchFinancials(@PathVariable String stockCode) {
        int count = dartFinancialService.fetchAndSave(stockCode);
        return ApiResponse.ok("financial statements fetched", Map.of("stockCode", stockCode, "savedCount", count));
    }

    @GetMapping("/dart/{stockCode}/financials")
    public ApiResponse<List<DartFinancialStatement>> getFinancials(@PathVariable String stockCode) {
        return ApiResponse.ok(dartFinancialService.getStatements(stockCode));
    }

    @PostMapping("/dart/{stockCode}/events/fetch")
    public ApiResponse<Map<String, Object>> fetchMajorEvents(@PathVariable String stockCode) {
        int count = dartMajorEventService.classifyAndSave(stockCode);
        return ApiResponse.ok("major events fetched", Map.of("stockCode", stockCode, "savedCount", count));
    }

    @GetMapping("/dart/{stockCode}/events")
    public ApiResponse<List<DartMajorEvent>> getMajorEvents(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.ok(dartMajorEventService.getMajorEvents(stockCode, limit));
    }
}
