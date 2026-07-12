package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.dto.KisCurrentPriceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisMapperTest {

    @Test
    @DisplayName("Maps KIS current price stock name")
    void mapsStockName() {
        KisCurrentPriceResponse response = new KisCurrentPriceResponse(
                "0",
                "OK",
                "success",
                new KisCurrentPriceResponse.Output(
                        "70000",
                        "1000",
                        "1.45",
                        "123456",
                        "1000000000",
                        "Samsung Electronics"
                )
        );

        StockQuoteResult result = KisMapper.toStockQuoteResult("005930", response);

        assertThat(result.getStockCode()).isEqualTo("005930");
        assertThat(result.getStockName()).isEqualTo("Samsung Electronics");
    }
}
