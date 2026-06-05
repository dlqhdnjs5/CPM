package com.bowon.cpm.admin;

import com.bowon.cpm.admin.service.StockDataBootstrapService;
import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.market.service.TechnicalIndicatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
class MarketControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MarketDataService marketDataService;
    @MockBean TechnicalIndicatorService technicalIndicatorService;
    @MockBean StockDataBootstrapService stockDataBootstrapService;

    @Test
    @DisplayName("POST /api/stocks/{stockCode}/ai-data/bootstrap delegates to bootstrap service")
    void bootstrapAiData() throws Exception {
        when(stockDataBootstrapService.bootstrap(
                eq("042700"),
                eq(LocalDate.of(2026, 3, 1)),
                eq(LocalDate.of(2026, 6, 5)),
                eq("한미반도체"),
                eq(30),
                eq(50)
        )).thenReturn(new StockDataBootstrapService.BootstrapResult(
                "042700",
                "042700",
                "한미반도체",
                "00161383",
                "한미반도체",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 6, 5),
                true,
                List.of(new StockDataBootstrapService.BootstrapStep("newsFetch", true, 30, null, 10))
        ));

        mvc.perform(post("/api/stocks/042700/ai-data/bootstrap")
                        .param("from", "2026-03-01")
                        .param("to", "2026-06-05")
                        .param("keyword", "한미반도체")
                        .param("newsDisplay", "30")
                        .param("newsAnalyzeLimit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("042700"))
                .andExpect(jsonPath("$.data.allSucceeded").value(true))
                .andExpect(jsonPath("$.data.steps[0].stepName").value("newsFetch"));

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(stockDataBootstrapService).bootstrap(
                eq("042700"),
                fromCaptor.capture(),
                eq(LocalDate.of(2026, 6, 5)),
                eq("한미반도체"),
                eq(30),
                eq(50)
        );
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 3, 1));
    }
}
