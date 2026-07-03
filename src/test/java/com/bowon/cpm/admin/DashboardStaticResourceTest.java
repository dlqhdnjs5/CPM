package com.bowon.cpm.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardStaticResourceTest {

    @Test
    @DisplayName("dashboard static resources contain the expected shell")
    void dashboardStaticResources() throws Exception {
        String html = new ClassPathResource("static/dashboard.html")
                .getContentAsString(StandardCharsets.UTF_8);
        String css = new ClassPathResource("static/dashboard.css")
                .getContentAsString(StandardCharsets.UTF_8);
        String js = new ClassPathResource("static/dashboard.js")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("CPM Dashboard", "side-menu", "분석 대상 종목", "stockSearchInput", "stockCandidateList", "stockMasterList", "positionsBody", "decisionsList");
        assertThat(html).doesNotContain("activeMarketTypeInput", "activeStockNameInput");
        assertThat(css).contains(".side-menu", ".section-grid", ".stock-chip", ".metric", ".panel");
        assertThat(js).contains("/api/dashboard/summary", "/api/stocks/candidates", "/api/stocks/active", "toggleStockActive", "stockLabel", "runAiDecision");
    }
}
