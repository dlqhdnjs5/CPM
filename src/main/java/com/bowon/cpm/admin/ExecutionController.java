package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.domain.OrderExecution;
import com.bowon.cpm.order.mapper.OrderExecutionMapper;
import com.bowon.cpm.order.service.ExecutionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionSyncService executionSyncService;
    private final OrderExecutionMapper orderExecutionMapper;
    private final TradingProperties tradingProperties;

    /**
     * 당일 체결 내역 동기화
     * POST /api/orders/executions/sync
     *
     * KIS 체결 조회 → order_execution 저장 → order_request FILLED 업데이트 → 포트폴리오 갱신
     */
    @PostMapping("/executions/sync")
    public ApiResponse<Map<String, Object>> syncExecutions() {
        int count = executionSyncService.syncExecutions();
        return ApiResponse.ok("체결 동기화 완료", Map.of("savedCount", count));
    }

    /**
     * 체결 내역 조회
     * GET /api/orders/executions?stockCode=005930&limit=20
     */
    @GetMapping("/executions")
    public ApiResponse<List<OrderExecution>> getExecutions(
            @RequestParam String stockCode,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(orderExecutionMapper.findByStockCode(stockCode, normalizeMode(mode), limit));
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null || mode.isBlank()
                ? tradingProperties.normalizedMode()
                : mode.trim().toUpperCase();
        if ("REAL".equals(normalized)) {
            return "KIS";
        }
        return normalized;
    }
}

