package com.bowon.cpm.dart.mapper;

import com.bowon.cpm.dart.domain.DartStockQuantity;
import com.bowon.cpm.support.TestProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ActiveProfiles(TestProfiles.TEST)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DartStockQuantityMapperTest {

    @Autowired DartStockQuantityMapper mapper;

    @Test
    @DisplayName("upserts stock quantity and finds latest by stock code")
    void upsertAndFindLatest() {
        DartStockQuantity quantity = DartStockQuantity.builder()
                .corpCode("99999991")
                .stockCode("999991")
                .businessYear(2025)
                .reportCode("11011")
                .stockType("common")
                .issuedStockQuantity(1_000_000L)
                .treasuryStockQuantity(100_000L)
                .distributedStockQuantity(900_000L)
                .settlementDate(LocalDate.of(2025, 12, 31))
                .rawJson("{}")
                .build();

        mapper.upsert(quantity);

        DartStockQuantity latest = mapper.findLatestByStockCode("999991").orElseThrow();
        assertThat(latest.getIssuedStockQuantity()).isEqualTo(1_000_000L);
        assertThat(latest.getDistributedStockQuantity()).isEqualTo(900_000L);
        assertThat(mapper.findByStockCode("999991", 10)).isNotEmpty();
    }
}
