package com.bowon.cpm.admin;

import com.bowon.cpm.feedback.service.PerformanceQueryService;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import com.bowon.cpm.risk.mapper.RiskPolicyConfigMapper;
import com.bowon.cpm.strategy.domain.StrategyConfig;
import com.bowon.cpm.strategy.mapper.StrategyConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPolicyController.class)
class AdminPolicyControllerTest {

    @Autowired MockMvc mvc;

    @MockBean RiskPolicyConfigMapper riskPolicyConfigMapper;
    @MockBean StrategyConfigMapper strategyConfigMapper;
    @MockBean PerformanceQueryService performanceQueryService;

    @Test
    @DisplayName("GET /api/admin/risk-policy returns active policies")
    void getRiskPolicies() throws Exception {
        when(riskPolicyConfigMapper.findAllActive()).thenReturn(List.of(
                RiskPolicyConfig.builder()
                        .policyCode("DEFAULT_RISK_POLICY")
                        .policyName("Default")
                        .minConfidence(new BigDecimal("0.70"))
                        .build()
        ));

        mvc.perform(get("/api/admin/risk-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].policyCode").value("DEFAULT_RISK_POLICY"));
    }

    @Test
    @DisplayName("PUT /api/admin/risk-policy/{policyCode} updates selected fields")
    void updateRiskPolicy() throws Exception {
        when(riskPolicyConfigMapper.findByPolicyCode("DEFAULT_RISK_POLICY"))
                .thenReturn(Optional.of(RiskPolicyConfig.builder()
                        .policyCode("DEFAULT_RISK_POLICY")
                        .policyName("Default")
                        .minConfidence(new BigDecimal("0.7500"))
                        .build()));

        mvc.perform(put("/api/admin/risk-policy/DEFAULT_RISK_POLICY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minConfidence": 0.75,
                                  "maxPositionWeight": 0.10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyCode").value("DEFAULT_RISK_POLICY"));

        ArgumentCaptor<RiskPolicyConfig> captor = ArgumentCaptor.forClass(RiskPolicyConfig.class);
        verify(riskPolicyConfigMapper).updateSelective(captor.capture());
        assertThat(captor.getValue().getPolicyCode()).isEqualTo("DEFAULT_RISK_POLICY");
        assertThat(captor.getValue().getMinConfidence()).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(captor.getValue().getMaxPositionWeight()).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    @DisplayName("PUT /api/admin/strategies/{strategyCode} updates strategy config")
    void updateStrategy() throws Exception {
        when(strategyConfigMapper.findByStrategyCode("AI_MOMENTUM"))
                .thenReturn(Optional.of(StrategyConfig.builder()
                        .strategyCode("AI_MOMENTUM")
                        .strategyName("AI Momentum")
                        .strategyType("AI")
                        .configJson("{\"enabled\":true}")
                        .build()));

        mvc.perform(put("/api/admin/strategies/AI_MOMENTUM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyName": "AI Momentum",
                                  "configJson": "{\\"enabled\\":true}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategyCode").value("AI_MOMENTUM"));

        verify(strategyConfigMapper).updateSelective(any(StrategyConfig.class));
    }

    @Test
    @DisplayName("GET /api/admin/performance/paper delegates to performance service")
    void getPaperPerformance() throws Exception {
        when(performanceQueryService.getPaperPerformance(10))
                .thenReturn(new PerformanceQueryService.PaperPerformance(
                        "12345678", Map.of("sell_count", 1), List.of(), List.of()
                ));

        mvc.perform(get("/api/admin/performance/paper").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNo").value("12345678"))
                .andExpect(jsonPath("$.data.aggregate.sell_count").value(1));
    }
}
