package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.dashboard.domain.DashboardAiTradeHistoryItem;
import com.bowon.cpm.dashboard.domain.DashboardSummary;
import com.bowon.cpm.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @GetMapping("/ai-trade-history")
    public ApiResponse<List<DashboardAiTradeHistoryItem>> aiTradeHistory(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "ALL") String decision,
            @RequestParam(defaultValue = "50") int limit
    ) {
        String resolvedFilter = filter != null && !filter.isBlank() ? filter : decision;
        return ApiResponse.ok(dashboardService.aiTradeHistory(resolvedFilter, limit));
    }
}
