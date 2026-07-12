package com.bowon.cpm.stock.service;

import com.bowon.cpm.stock.mapper.StockMasterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockMarketTypeServiceTest {

    private final StockMasterMapper mapper = mock(StockMasterMapper.class);
    private final StockMarketTypeService service = new StockMarketTypeService(mapper);

    @Test
    @DisplayName("updates normalized market type")
    void updateMarketType() {
        when(mapper.updateMarketType("036930", "KOSDAQ")).thenReturn(1);

        StockMarketTypeService.MarketTypeUpdateResult result =
                service.updateMarketType("036930", "kosdaq");

        assertThat(result.stockCode()).isEqualTo("036930");
        assertThat(result.marketType()).isEqualTo("KOSDAQ");
        assertThat(result.updated()).isTrue();
        verify(mapper).updateMarketType("036930", "KOSDAQ");
    }

    @Test
    @DisplayName("rejects unsupported market type")
    void rejectsUnsupportedMarketType() {
        assertThatThrownBy(() -> service.updateMarketType("036930", "NASDAQ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported marketType");
    }
}
