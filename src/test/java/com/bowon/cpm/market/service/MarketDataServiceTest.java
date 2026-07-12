package com.bowon.cpm.market.service;

import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisDailyPriceClient;
import com.bowon.cpm.broker.kis.dto.KisDailyPriceResponse;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.market.mapper.StockRealtimeQuoteMapper;
import com.bowon.cpm.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock BrokerClient brokerClient;
    @Mock KisDailyPriceClient dailyPriceClient;
    @Mock StockRealtimeQuoteMapper realtimeQuoteMapper;
    @Mock StockPriceDailyMapper stockPriceDailyMapper;
    @Mock BrokerApiLogMapper brokerApiLogMapper;
    @Mock StockService stockService;
    Clock clock = Clock.fixed(Instant.parse("2026-06-05T07:00:00Z"), ZoneId.of("Asia/Seoul"));

    MarketDataService service;

    @BeforeEach
    void setUp() {
        service = new MarketDataService(
                brokerClient,
                dailyPriceClient,
                realtimeQuoteMapper,
                stockPriceDailyMapper,
                brokerApiLogMapper,
                stockService,
                clock
        );
    }

    @Test
    @DisplayName("Daily price sync saves stock name from current quote")
    void dailyPriceSyncSavesStockNameFromQuote() {
        when(dailyPriceClient.getDailyPrice("005930", "D")).thenReturn(new KisDailyPriceResponse(
                "0",
                "OK",
                "success",
                List.of(new KisDailyPriceResponse.DailyOutput(
                        "20260605",
                        "69000",
                        "71000",
                        "68000",
                        "70000",
                        "123456",
                        "1000000000"
                ))
        ));
        when(brokerClient.getCurrentPrice("005930")).thenReturn(StockQuoteResult.builder()
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .currentPrice(new BigDecimal("70000"))
                .build());

        service.fetchAndSaveDailyPrices("005930");

        verify(stockService).upsertStockMaster("005930", "Samsung Electronics", "UNKNOWN");
    }

    @Test
    @DisplayName("Daily price sync calculates trading value from close price and volume")
    void dailyPriceSyncCalculatesTradingValue() {
        when(dailyPriceClient.getDailyPrice("005930", "D")).thenReturn(new KisDailyPriceResponse(
                "0",
                "OK",
                "success",
                List.of(new KisDailyPriceResponse.DailyOutput(
                        "20260605",
                        "69000",
                        "71000",
                        "68000",
                        "70000",
                        "123456",
                        null
                ))
        ));
        when(brokerClient.getCurrentPrice("005930")).thenReturn(StockQuoteResult.builder()
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .currentPrice(new BigDecimal("70000"))
                .build());

        service.fetchAndSaveDailyPrices("005930");

        var captor = forClass(List.class);
        verify(stockPriceDailyMapper).insertBatch(captor.capture());
        @SuppressWarnings("unchecked")
        List<com.bowon.cpm.market.domain.StockPriceDaily> saved = captor.getValue();
        assertThat(saved.get(0).getTradingValue()).isEqualByComparingTo("8641920000");
    }
}
