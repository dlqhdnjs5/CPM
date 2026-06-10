package com.bowon.cpm.admin;

import com.bowon.cpm.macro.domain.MacroContext;
import com.bowon.cpm.macro.service.MacroContextService;
import com.bowon.cpm.macro.service.MacroNewsService;
import com.bowon.cpm.news.service.NewsAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MacroController.class)
class MacroControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MacroNewsService macroNewsService;
    @MockBean MacroContextService macroContextService;

    @Test
    @DisplayName("POST /api/macro/news/fetch delegates to macro news service")
    void fetchMacroNews() throws Exception {
        when(macroNewsService.collectDefault(eq(3), eq(false), eq(10)))
                .thenReturn(new MacroNewsService.MacroCollectResult(
                        9,
                        Map.of(MacroNewsService.FED_CODE, 3),
                        new NewsAnalysisService.AnalysisBatchResult(0, 0, 0)
                ));

        mvc.perform(post("/api/macro/news/fetch")
                        .param("displayPerKeyword", "3")
                        .param("analyze", "false")
                        .param("analyzeLimit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSaved").value(9))
                .andExpect(jsonPath("$.data.savedByCode.MACRO_FED").value(3));

        verify(macroNewsService).collectDefault(3, false, 10);
    }

    @Test
    @DisplayName("GET /api/macro/context/latest returns aggregated macro context")
    void getLatestMacroContext() throws Exception {
        when(macroContextService.latestContext(eq(5), eq(10)))
                .thenReturn(new MacroContext(
                        new MacroContext.MacroSignal("MACRO_FED", "HAWKISH", new BigDecimal("-0.2"),
                                new BigDecimal("0.8"), 2, List.of(), "fed summary"),
                        new MacroContext.MacroSignal("MACRO_BOK", "NEUTRAL", BigDecimal.ZERO,
                                new BigDecimal("0.3"), 1, List.of(), "bok summary"),
                        new MacroContext.MacroSignal("MACRO_MARKET", "RISK_OFF", new BigDecimal("-0.1"),
                                new BigDecimal("0.5"), 1, List.of(), "market summary"),
                        new BigDecimal("0.6"),
                        LocalDateTime.of(2026, 6, 10, 1, 0)
                ));

        mvc.perform(get("/api/macro/context/latest")
                        .param("days", "5")
                        .param("limitPerSignal", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fed.stance").value("HAWKISH"))
                .andExpect(jsonPath("$.data.combinedRiskScore").value(0.6));

        verify(macroContextService).latestContext(5, 10);
    }
}
