package com.bowon.cpm.market.service;

import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisDailyPriceClient;
import com.bowon.cpm.broker.kis.dto.KisDailyPriceResponse;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.stock.service.StockService;
import com.bowon.cpm.support.TestProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MybatisTest
@ActiveProfiles(TestProfiles.TEST)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MarketDataService.class, StockService.class})
class MarketDataServiceIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired MarketDataService service;
    @Autowired StockPriceDailyMapper stockPriceDailyMapper;

    @MockBean BrokerClient brokerClient;
    @MockBean KisDailyPriceClient dailyPriceClient;
    @MockBean Clock clock;

    @BeforeEach
    void setUp() {
        when(brokerClient.getCurrentPrice("999961")).thenReturn(quote("999961"));
        when(brokerClient.getCurrentPrice("999962")).thenReturn(quote("999962"));
    }

    @Test
    @DisplayName("before close, today's KIS daily row is not persisted to stock_price_daily")
    void beforeCloseSkipsTodayDailyRow() {
        setClock(LocalDateTime.of(2026, 6, 10, 9, 0));
        when(dailyPriceClient.getDailyPrice("999961", "D")).thenReturn(new KisDailyPriceResponse(
                "0",
                "OK",
                "success",
                List.of(
                        output("20260610", "1000", "1000", "1000", "1000", "10", "10000"),
                        output("20260609", "900", "1100", "850", "950", "1000", "950000")
                )
        ));

        int saved = service.fetchAndSaveDailyPrices("999961");

        List<StockPriceDaily> prices = stockPriceDailyMapper.findByStockCodeAndDateRange(
                "999961",
                LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 6, 10)
        );

        assertThat(saved).isEqualTo(1);
        assertThat(prices)
                .extracting(StockPriceDaily::getTradeDate)
                .containsExactly(LocalDate.of(2026, 6, 9));
        assertThat(prices.get(0).getClosePrice()).isEqualByComparingTo(new BigDecimal("950"));
        assertThat(prices.get(0).getVolume()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("after close, today's confirmed daily row is persisted and later corrected by upsert")
    void afterClosePersistsTodayAndUpsertsCorrection() {
        setClock(LocalDateTime.of(2026, 6, 10, 16, 10));
        when(dailyPriceClient.getDailyPrice("999962", "D"))
                .thenReturn(new KisDailyPriceResponse(
                        "0",
                        "OK",
                        "success",
                        List.of(output("20260610", "1000", "1100", "900", "1000", "100", "100000"))
                ))
                .thenReturn(new KisDailyPriceResponse(
                        "0",
                        "OK",
                        "success",
                        List.of(output("20260610", "1000", "1300", "900", "1200", "200", "240000"))
                ));

        int firstSaved = service.fetchAndSaveDailyPrices("999962");
        int secondSaved = service.fetchAndSaveDailyPrices("999962");

        List<StockPriceDaily> prices = stockPriceDailyMapper.findByStockCodeAndDateRange(
                "999962",
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10)
        );

        assertThat(firstSaved).isEqualTo(1);
        assertThat(secondSaved).isEqualTo(1);
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).getClosePrice()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(prices.get(0).getHighPrice()).isEqualByComparingTo(new BigDecimal("1300"));
        assertThat(prices.get(0).getVolume()).isEqualTo(200L);
    }

    private void setClock(LocalDateTime dateTime) {
        when(clock.getZone()).thenReturn(SEOUL);
        when(clock.instant()).thenReturn(dateTime.atZone(SEOUL).toInstant());
    }

    private StockQuoteResult quote(String stockCode) {
        return StockQuoteResult.builder()
                .stockCode(stockCode)
                .stockName("Integration Test " + stockCode)
                .currentPrice(new BigDecimal("1000"))
                .build();
    }

    private KisDailyPriceResponse.DailyOutput output(
            String date,
            String open,
            String high,
            String low,
            String close,
            String volume,
            String tradingValue
    ) {
        return new KisDailyPriceResponse.DailyOutput(date, open, high, low, close, volume, tradingValue);
    }
}
