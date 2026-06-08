package com.bowon.cpm.stock.mapper;

import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.support.TestProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ActiveProfiles(TestProfiles.TEST)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockMasterMapperTest {

    @Autowired StockMasterMapper mapper;

    @Test
    @DisplayName("UNKNOWN upsert preserves known market type and updateMarketType changes it")
    void marketTypeUpdateAndUnknownPreservation() {
        mapper.upsert(StockMaster.builder()
                .stockCode("999981")
                .stockName("Mapper Test")
                .marketType("KOSDAQ")
                .isActive(true)
                .isWatched(false)
                .build());

        mapper.upsert(StockMaster.builder()
                .stockCode("999981")
                .stockName("Mapper Test Renamed")
                .marketType("UNKNOWN")
                .isActive(true)
                .isWatched(false)
                .build());

        assertThat(mapper.findByStockCode("999981")).isPresent()
                .get()
                .extracting(StockMaster::getMarketType)
                .isEqualTo("KOSDAQ");

        int updated = mapper.updateMarketType("999981", "KOSPI");
        assertThat(updated).isEqualTo(1);

        assertThat(mapper.findByStockCode("999981")).isPresent()
                .get()
                .extracting(StockMaster::getMarketType)
                .isEqualTo("KOSPI");
    }
}
