package com.bowon.cpm.market.service;

import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.mapper.StockIndicatorDailyMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicalIndicatorServiceTest {

    @Mock StockMasterMapper stockMasterMapper;
    @Mock StockPriceDailyMapper stockPriceDailyMapper;
    @Mock StockIndicatorDailyMapper stockIndicatorDailyMapper;

    TechnicalIndicatorService service;

    @BeforeEach
    void setUp() {
        service = new TechnicalIndicatorService(
                new TechnicalIndicatorCalculator(),
                stockMasterMapper,
                stockPriceDailyMapper,
                stockIndicatorDailyMapper
        );
    }

    @Test
    @DisplayName("Calculates and upserts indicator for one stock")
    void calculateForStockUpsertsIndicator() {
        when(stockPriceDailyMapper.findByStockCodeAndDateRange(eq("005930"), any(), any()))
                .thenReturn(List.of(
                        price("005930", LocalDate.of(2026, 1, 1), "1000"),
                        price("005930", LocalDate.of(2026, 1, 2), "1010"),
                        price("005930", LocalDate.of(2026, 1, 3), "1020"),
                        price("005930", LocalDate.of(2026, 1, 4), "1030"),
                        price("005930", LocalDate.of(2026, 1, 5), "1040")
                ));

        StockIndicatorDaily indicator = service.calculateForStock("005930");

        assertThat(indicator).isNotNull();
        assertThat(indicator.getMa5()).isEqualByComparingTo(new BigDecimal("1020.00"));
        verify(stockIndicatorDailyMapper).upsert(any(StockIndicatorDaily.class));
    }

    @Test
    @DisplayName("Skips stock without daily prices")
    void skipsWithoutPrices() {
        when(stockPriceDailyMapper.findByStockCodeAndDateRange(eq("005930"), any(), any()))
                .thenReturn(List.of());

        assertThat(service.calculateForStock("005930")).isNull();
        verify(stockIndicatorDailyMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("Calculates all active stocks and returns saved count")
    void calculatesAllActiveStocks() {
        when(stockMasterMapper.findAllActive()).thenReturn(List.of(
                StockMaster.builder().stockCode("005930").build(),
                StockMaster.builder().stockCode("000660").build()
        ));
        when(stockPriceDailyMapper.findByStockCodeAndDateRange(any(), any(), any()))
                .thenReturn(List.of(
                        price("005930", LocalDate.of(2026, 1, 1), "1000"),
                        price("005930", LocalDate.of(2026, 1, 2), "1010"),
                        price("005930", LocalDate.of(2026, 1, 3), "1020"),
                        price("005930", LocalDate.of(2026, 1, 4), "1030"),
                        price("005930", LocalDate.of(2026, 1, 5), "1040")
                ));

        int saved = service.calculateAllActive();

        assertThat(saved).isEqualTo(2);
        verify(stockIndicatorDailyMapper, times(2)).upsert(any(StockIndicatorDaily.class));
    }

    private StockPriceDaily price(String stockCode, LocalDate date, String close) {
        return StockPriceDaily.builder()
                .stockCode(stockCode)
                .tradeDate(date)
                .closePrice(new BigDecimal(close))
                .volume(1000L)
                .build();
    }
}
