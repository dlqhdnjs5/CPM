package com.bowon.cpm.market.service;

import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalIndicatorCalculatorTest {

    private final TechnicalIndicatorCalculator calculator = new TechnicalIndicatorCalculator();

    @Test
    @DisplayName("Calculates MA, RSI, MACD, Bollinger, volatility and volume change")
    void calculatesLatestIndicators() {
        List<StockPriceDaily> prices = increasingPrices(130);

        StockIndicatorDaily indicator = calculator.calculateLatest("005930", prices);

        assertThat(indicator).isNotNull();
        assertThat(indicator.getStockCode()).isEqualTo("005930");
        assertThat(indicator.getTradeDate()).isEqualTo(LocalDate.of(2026, 5, 10).plusDays(129));
        assertThat(indicator.getMa5()).isEqualByComparingTo(new BigDecimal("1127.00"));
        assertThat(indicator.getMa20()).isEqualByComparingTo(new BigDecimal("1119.50"));
        assertThat(indicator.getMa60()).isEqualByComparingTo(new BigDecimal("1099.50"));
        assertThat(indicator.getMa120()).isEqualByComparingTo(new BigDecimal("1069.50"));
        assertThat(indicator.getRsi14()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(indicator.getMacd()).isNotNull();
        assertThat(indicator.getMacdSignal()).isNotNull();
        assertThat(indicator.getMacdHistogram()).isNotNull();
        assertThat(indicator.getBollingerUpper()).isGreaterThan(indicator.getBollingerMiddle());
        assertThat(indicator.getBollingerLower()).isLessThan(indicator.getBollingerMiddle());
        assertThat(indicator.getVolatility()).isNotNull();
        assertThat(indicator.getVolumeChangeRate()).isNotNull();
    }

    @Test
    @DisplayName("Returns partial indicators when price history is short")
    void returnsPartialIndicatorsForShortHistory() {
        StockIndicatorDaily indicator = calculator.calculateLatest("005930", increasingPrices(10));

        assertThat(indicator).isNotNull();
        assertThat(indicator.getMa5()).isEqualByComparingTo(new BigDecimal("1007.00"));
        assertThat(indicator.getMa20()).isNull();
        assertThat(indicator.getRsi14()).isNull();
        assertThat(indicator.getMacd()).isNull();
    }

    @Test
    @DisplayName("Returns null when no usable price exists")
    void returnsNullWithoutUsablePrices() {
        assertThat(calculator.calculateLatest("005930", List.of())).isNull();
    }

    private List<StockPriceDaily> increasingPrices(int count) {
        List<StockPriceDaily> prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 5, 10);
        for (int i = 0; i < count; i++) {
            BigDecimal close = BigDecimal.valueOf(1000L + i);
            prices.add(StockPriceDaily.builder()
                    .stockCode("005930")
                    .tradeDate(start.plusDays(i))
                    .openPrice(close.subtract(BigDecimal.ONE))
                    .highPrice(close.add(BigDecimal.TEN))
                    .lowPrice(close.subtract(BigDecimal.TEN))
                    .closePrice(close)
                    .volume(1000L + i * 10L)
                    .build());
        }
        return prices;
    }
}
