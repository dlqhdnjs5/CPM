package com.bowon.cpm.admin;

import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.mapper.OrderExecutionMapper;
import com.bowon.cpm.order.service.ExecutionSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @Autowired MockMvc mvc;

    @MockBean ExecutionSyncService executionSyncService;
    @MockBean OrderExecutionMapper orderExecutionMapper;
    @MockBean TradingProperties tradingProperties;

    @Test
    @DisplayName("Execution query defaults to current trading mode")
    void executionQueryDefaultsToCurrentMode() throws Exception {
        when(tradingProperties.normalizedMode()).thenReturn("PAPER");
        when(orderExecutionMapper.findByStockCode("005930", "PAPER", 20))
                .thenReturn(List.of());

        mvc.perform(get("/api/orders/executions")
                        .param("stockCode", "005930"))
                .andExpect(status().isOk());

        verify(orderExecutionMapper).findByStockCode("005930", "PAPER", 20);
    }

    @Test
    @DisplayName("Execution query maps REAL mode to KIS broker type")
    void executionQueryMapsRealToKis() throws Exception {
        when(orderExecutionMapper.findByStockCode("005930", "KIS", 20))
                .thenReturn(List.of());

        mvc.perform(get("/api/orders/executions")
                        .param("stockCode", "005930")
                        .param("mode", "REAL"))
                .andExpect(status().isOk());

        verify(orderExecutionMapper).findByStockCode("005930", "KIS", 20);
    }

    @Test
    @DisplayName("Execution query supports explicit ALL mode")
    void executionQuerySupportsAllMode() throws Exception {
        when(orderExecutionMapper.findByStockCode("005930", "ALL", 5))
                .thenReturn(List.of());

        mvc.perform(get("/api/orders/executions")
                        .param("stockCode", "005930")
                        .param("mode", "ALL")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(orderExecutionMapper).findByStockCode("005930", "ALL", 5);
    }
}
