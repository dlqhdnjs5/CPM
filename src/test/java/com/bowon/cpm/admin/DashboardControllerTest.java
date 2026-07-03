package com.bowon.cpm.admin;

import com.bowon.cpm.dashboard.domain.DashboardSummary;
import com.bowon.cpm.dashboard.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired MockMvc mvc;

    @MockBean DashboardService dashboardService;

    @Test
    @DisplayName("GET /api/dashboard/summary returns dashboard summary")
    void summary() throws Exception {
        when(dashboardService.summary()).thenReturn(new DashboardSummary(
                LocalDateTime.of(2026, 7, 3, 10, 0),
                new DashboardSummary.SystemStatus("PAPER", false, "1234****", 3),
                List.of(),
                List.of(),
                null,
                List.of(),
                new DashboardSummary.DecisionSummary(1, 2, 0, 1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.system.tradingMode").value("PAPER"))
                .andExpect(jsonPath("$.data.system.activeStockCount").value(3))
                .andExpect(jsonPath("$.data.decisions.buyCount").value(1));
    }
}
