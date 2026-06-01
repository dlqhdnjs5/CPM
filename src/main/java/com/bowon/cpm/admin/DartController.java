package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.dart.domain.DartDisclosure;
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
}


