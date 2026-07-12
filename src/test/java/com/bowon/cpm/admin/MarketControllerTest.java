package com.bowon.cpm.admin;

import com.bowon.cpm.admin.service.StockDataBootstrapService;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
import com.bowon.cpm.market.domain.StockSupplyDemandDaily;
import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.market.service.SupplyDemandService;
import com.bowon.cpm.market.service.TechnicalIndicatorService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
class MarketControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MarketDataService marketDataService;
    @MockBean TechnicalIndicatorService technicalIndicatorService;
    @MockBean StockDataBootstrapService stockDataBootstrapService;
    @MockBean SupplyDemandService supplyDemandService;
    @MockBean StockMasterMapper stockMasterMapper;
    @MockBean DartCorpCodeMapper dartCorpCodeMapper;

    @Test
    @DisplayName("GET /api/stocks/candidates searches inactive DART listed stocks")
    void searchStockCandidates() throws Exception {
        when(dartCorpCodeMapper.searchInactiveListedCandidates("삼성", 10)).thenReturn(List.of(
                DartCorpCode.builder()
                        .stockCode("005930")
                        .corpName("삼성전자")
                        .corpCode("00126380")
                        .build()
        ));

        mvc.perform(get("/api/stocks/candidates")
                        .param("query", "삼성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.data[0].stockName").value("삼성전자"))
                .andExpect(jsonPath("$.data[0].corpCode").value("00126380"));
    }

    @Test
    @DisplayName("POST /api/stocks/active activates a DART stock and runs bootstrap")
    void addActiveStock() throws Exception {
        when(dartCorpCodeMapper.findByStockCode("005930")).thenReturn(Optional.of(
                DartCorpCode.builder()
                        .stockCode("005930")
                        .corpName("Samsung Electronics")
                        .corpCode("00126380")
                        .build()
        ));
        when(stockDataBootstrapService.bootstrap(
                eq("005930"),
                eq(null),
                eq(null),
                eq(null),
                eq(30),
                eq(20)
        )).thenReturn(new StockDataBootstrapService.BootstrapResult(
                "005930",
                "005930",
                "Samsung Electronics",
                "00126380",
                "Samsung Electronics",
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 7, 3),
                true,
                List.of(new StockDataBootstrapService.BootstrapStep("stockMasterUpsert", true, 1, null, 10))
        ));
        when(stockMasterMapper.findByStockCode("005930")).thenReturn(Optional.of(
                StockMaster.builder()
                        .stockCode("005930")
                        .stockName("Samsung Electronics")
                        .marketType("KOSPI")
                        .isActive(true)
                        .build()
        ));

        mvc.perform(post("/api/stocks/active")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCode": "005930",
                                  "newsDisplay": 30,
                                  "newsAnalyzeLimit": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.stock.stockName").value("Samsung Electronics"))
                .andExpect(jsonPath("$.data.stock.active").value(true))
                .andExpect(jsonPath("$.data.bootstrap.stockCode").value("005930"));
    }

    @Test
    @DisplayName("PATCH /api/stocks/{stockCode}/active updates active flag")
    void updateActiveStock() throws Exception {
        when(stockMasterMapper.findByStockCode("005930")).thenReturn(Optional.of(
                StockMaster.builder()
                        .stockCode("005930")
                        .stockName("Samsung Electronics")
                        .marketType("KOSPI")
                        .isActive(false)
                        .build()
        ));

        mvc.perform(patch("/api/stocks/005930/active")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("005930"));

        verify(stockMasterMapper).updateActive("005930", true);
    }

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

    @Test
    @DisplayName("POST /api/stocks/{stockCode}/supply-demand/fetch delegates to supply demand service")
    void fetchSupplyDemand() throws Exception {
        when(supplyDemandService.fetchAndSave("005930")).thenReturn(3);

        mvc.perform(post("/api/stocks/005930/supply-demand/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.savedCount").value(3));

        verify(supplyDemandService).fetchAndSave("005930");
    }

    @Test
    @DisplayName("GET /api/stocks/{stockCode}/supply-demand returns recent investor flow")
    void getSupplyDemand() throws Exception {
        when(supplyDemandService.getRecent("005930", 5)).thenReturn(List.of(
                StockSupplyDemandDaily.builder()
                        .stockCode("005930")
                        .tradeDate(LocalDate.of(2026, 6, 10))
                        .foreignNetBuyAmount(new BigDecimal("1200000000"))
                        .institutionNetBuyAmount(new BigDecimal("800000000"))
                        .individualNetBuyAmount(new BigDecimal("-2000000000"))
                        .build()
        ));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/stocks/005930/supply-demand")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.data[0].foreignNetBuyAmount").value(1200000000));

        verify(supplyDemandService).getRecent("005930", 5);
    }
}
