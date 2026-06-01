package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartFinancialStatement;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.dart.service.DartMajorEventService;
import com.bowon.cpm.dart.service.DartService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DartController {

    private final DartService dartService;
    private final DartFinancialService dartFinancialService;
    private final DartMajorEventService dartMajorEventService;

    /**
     * DART corp_code 전체 동기화
     * POST /api/dart/corp-codes/sync
     */
    @PostMapping("/dart/corp-codes/sync")
    public ApiResponse<Map<String, Object>> syncCorpCodes() {
        int count = dartService.syncCorpCodes();
        return ApiResponse.ok("corp_code 동기화 완료", Map.of("savedCount", count));
    }

    /**
     * 종목 공시 수집
     * POST /api/dart/{stockCode}/disclosures/fetch?from=2026-01-01&to=2026-05-31
     */
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
        return ApiResponse.ok("공시 수집 완료", Map.of("stockCode", stockCode, "savedCount", count));
    }

    /**
     * 저장된 공시 조회
     * GET /api/stocks/{stockCode}/disclosures?from=2026-01-01&to=2026-05-31
     */
    @GetMapping("/stocks/{stockCode}/disclosures")
    public ApiResponse<List<DartDisclosure>> getDisclosures(
            @PathVariable String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.ok(dartService.getDisclosures(stockCode, from, to));
    }

    /**
     * 재무제표 수집
     * POST /api/dart/{stockCode}/financials/fetch
     */
    @PostMapping("/dart/{stockCode}/financials/fetch")
    public ApiResponse<Map<String, Object>> fetchFinancials(@PathVariable String stockCode) {
        int count = dartFinancialService.fetchAndSave(stockCode);
        return ApiResponse.ok("재무제표 수집 완료", Map.of("stockCode", stockCode, "savedCount", count));
    }

    /**
     * 저장된 재무제표 조회
     * GET /api/dart/{stockCode}/financials
     */
    @GetMapping("/dart/{stockCode}/financials")
    public ApiResponse<List<DartFinancialStatement>> getFinancials(@PathVariable String stockCode) {
        return ApiResponse.ok(dartFinancialService.getStatements(stockCode));
    }

    /**
     * 주요 이벤트 분류 + 저장 + OpenAI 요약
     * POST /api/dart/{stockCode}/events/fetch
     */
    @PostMapping("/dart/{stockCode}/events/fetch")
    public ApiResponse<Map<String, Object>> fetchMajorEvents(@PathVariable String stockCode) {
        int count = dartMajorEventService.classifyAndSave(stockCode);
        return ApiResponse.ok("주요 이벤트 수집 완료", Map.of("stockCode", stockCode, "savedCount", count));
    }

    /**
     * 저장된 주요 이벤트 조회
     * GET /api/dart/{stockCode}/events
     */
    @GetMapping("/dart/{stockCode}/events")
    public ApiResponse<List<DartMajorEvent>> getMajorEvents(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.ok(dartMajorEventService.getMajorEvents(stockCode, limit));
    }
}


