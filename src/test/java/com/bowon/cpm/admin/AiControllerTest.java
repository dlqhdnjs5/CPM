package com.bowon.cpm.admin;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.service.AiDecisionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
class AiControllerTest {

    @Autowired MockMvc mvc;

    @MockBean AiDecisionService aiDecisionService;

    @Test
    @DisplayName("POST /api/ai/decisions?stockCode=005930 creates AI decision")
    void generateDecisionByQueryParam() throws Exception {
        when(aiDecisionService.generateDecision("005930")).thenReturn(decision());

        mvc.perform(post("/api/ai/decisions")
                        .param("stockCode", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.decision").value("BUY"))
                .andExpect(jsonPath("$.data.confidence").value(0.85));

        verify(aiDecisionService).generateDecision("005930");
    }

    @Test
    @DisplayName("POST /api/ai/decisions/{stockCode} remains supported")
    void generateDecisionByPathVariable() throws Exception {
        when(aiDecisionService.generateDecision("005930")).thenReturn(decision());

        mvc.perform(post("/api/ai/decisions/005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("005930"));

        verify(aiDecisionService).generateDecision("005930");
    }

    @Test
    @DisplayName("GET /api/ai/decisions keeps list lookup behavior")
    void getDecisions() throws Exception {
        when(aiDecisionService.getDecisions("005930", 10)).thenReturn(List.of(decision()));

        mvc.perform(get("/api/ai/decisions")
                        .param("stockCode", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stockCode").value("005930"));

        verify(aiDecisionService).getDecisions("005930", 10);
    }

    @Test
    @DisplayName("GET /api/ai/decisions/{id} keeps detail lookup behavior")
    void getDecision() throws Exception {
        when(aiDecisionService.getDecision(1L)).thenReturn(Optional.of(decision()));

        mvc.perform(get("/api/ai/decisions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("005930"));

        verify(aiDecisionService).getDecision(1L);
    }

    private AiDecision decision() {
        return AiDecision.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .decision("BUY")
                .confidence(new BigDecimal("0.85"))
                .build();
    }
}
