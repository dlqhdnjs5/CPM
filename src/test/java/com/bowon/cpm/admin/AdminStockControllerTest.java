package com.bowon.cpm.admin;

import com.bowon.cpm.stock.service.StockMarketTypeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStockController.class)
class AdminStockControllerTest {

    @Autowired MockMvc mvc;

    @MockBean StockMarketTypeService stockMarketTypeService;

    @Test
    @DisplayName("PATCH /api/admin/stocks/{stockCode}/market-type delegates to service")
    void updateMarketType() throws Exception {
        when(stockMarketTypeService.updateMarketType("036930", "KOSDAQ"))
                .thenReturn(new StockMarketTypeService.MarketTypeUpdateResult("036930", "KOSDAQ", true));

        mvc.perform(patch("/api/admin/stocks/036930/market-type")
                        .param("marketType", "KOSDAQ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("036930"))
                .andExpect(jsonPath("$.data.marketType").value("KOSDAQ"))
                .andExpect(jsonPath("$.data.updated").value(true));

        verify(stockMarketTypeService).updateMarketType("036930", "KOSDAQ");
    }

    @Test
    @DisplayName("PATCH /api/admin/stocks/market-types delegates bulk request")
    void updateMarketTypes() throws Exception {
        when(stockMarketTypeService.updateMarketTypes(eq(Map.of("005930", "KOSPI", "036930", "KOSDAQ"))))
                .thenReturn(new StockMarketTypeService.BulkMarketTypeUpdateResult(2, 2, Map.of()));

        mvc.perform(patch("/api/admin/stocks/market-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "005930": "KOSPI",
                                  "036930": "KOSDAQ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.updatedCount").value(2));

        verify(stockMarketTypeService).updateMarketTypes(Map.of("005930", "KOSPI", "036930", "KOSDAQ"));
    }
}
