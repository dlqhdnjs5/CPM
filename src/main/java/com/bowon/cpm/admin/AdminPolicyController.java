package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.feedback.service.PerformanceQueryService;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import com.bowon.cpm.risk.mapper.RiskPolicyConfigMapper;
import com.bowon.cpm.strategy.domain.StrategyConfig;
import com.bowon.cpm.strategy.mapper.StrategyConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPolicyController {

    private final RiskPolicyConfigMapper riskPolicyConfigMapper;
    private final StrategyConfigMapper strategyConfigMapper;
    private final PerformanceQueryService performanceQueryService;

    @GetMapping("/risk-policy")
    public ApiResponse<List<RiskPolicyConfig>> getRiskPolicies() {
        return ApiResponse.ok(riskPolicyConfigMapper.findAllActive());
    }

    @PutMapping("/risk-policy/{policyCode}")
    @Transactional
    public ApiResponse<RiskPolicyConfig> updateRiskPolicy(
            @PathVariable String policyCode,
            @RequestBody RiskPolicyUpdateRequest request
    ) {
        RiskPolicyConfig update = RiskPolicyConfig.builder()
                .policyCode(policyCode)
                .policyName(request.policyName())
                .minConfidence(request.minConfidence())
                .maxPositionWeight(request.maxPositionWeight())
                .maxOrderAmount(request.maxOrderAmount())
                .minRiskRewardRatio(request.minRiskRewardRatio())
                .maxExpectedLossRate(request.maxExpectedLossRate())
                .blockOverheatRate(request.blockOverheatRate())
                .isActive(request.isActive())
                .build();
        riskPolicyConfigMapper.updateSelective(update);
        RiskPolicyConfig saved = riskPolicyConfigMapper.findByPolicyCode(policyCode)
                .orElseThrow(() -> new IllegalArgumentException("risk policy not found: " + policyCode));
        return ApiResponse.ok("리스크 정책 수정 완료", saved);
    }

    @GetMapping("/strategies")
    public ApiResponse<List<StrategyConfig>> getStrategies() {
        return ApiResponse.ok(strategyConfigMapper.findAll());
    }

    @PutMapping("/strategies/{strategyCode}")
    @Transactional
    public ApiResponse<StrategyConfig> updateStrategy(
            @PathVariable String strategyCode,
            @RequestBody StrategyUpdateRequest request
    ) {
        StrategyConfig update = StrategyConfig.builder()
                .strategyCode(strategyCode)
                .strategyName(request.strategyName())
                .strategyType(request.strategyType())
                .configJson(request.configJson())
                .isActive(request.isActive())
                .build();
        strategyConfigMapper.updateSelective(update);
        StrategyConfig saved = strategyConfigMapper.findByStrategyCode(strategyCode)
                .orElseThrow(() -> new IllegalArgumentException("strategy not found: " + strategyCode));
        return ApiResponse.ok("전략 설정 수정 완료", saved);
    }

    @GetMapping("/performance/paper")
    public ApiResponse<PerformanceQueryService.PaperPerformance> getPaperPerformance(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(performanceQueryService.getPaperPerformance(limit));
    }

    public record RiskPolicyUpdateRequest(
            String policyName,
            BigDecimal minConfidence,
            BigDecimal maxPositionWeight,
            BigDecimal maxOrderAmount,
            BigDecimal minRiskRewardRatio,
            BigDecimal maxExpectedLossRate,
            BigDecimal blockOverheatRate,
            Boolean isActive
    ) {}

    public record StrategyUpdateRequest(
            String strategyName,
            String strategyType,
            String configJson,
            Boolean isActive
    ) {}
}
