package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    /**
     * AI 판단에 대한 리스크 검증 수행
     * POST /api/risk/checks/{aiDecisionId}
     */
    @PostMapping("/checks/{aiDecisionId}")
    public ApiResponse<Map<String, Object>> check(@PathVariable Long aiDecisionId) {
        RiskCheckResult result = riskService.checkAndSave(aiDecisionId);
        return ApiResponse.ok("리스크 검증 완료", Map.of(
                "aiDecisionId", aiDecisionId,
                "passed", result.getPassed(),
                "failReason", result.getFailReason() != null ? result.getFailReason() : "통과"
        ));
    }
}

