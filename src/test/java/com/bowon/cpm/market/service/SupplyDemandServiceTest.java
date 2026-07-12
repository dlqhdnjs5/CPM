package com.bowon.cpm.market.service;

import com.bowon.cpm.broker.kis.KisInvestorClient;
import com.bowon.cpm.broker.kis.dto.KisInvestorResponse;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.market.domain.StockSupplyDemandDaily;
import com.bowon.cpm.market.mapper.StockSupplyDemandDailyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyDemandServiceTest {

    @Mock KisInvestorClient kisInvestorClient;
    @Mock StockSupplyDemandDailyMapper supplyDemandMapper;
    @Mock BrokerApiLogMapper brokerApiLogMapper;
    @InjectMocks SupplyDemandService service;

    @Test
    @DisplayName("fetchAndSave maps KIS investor flow output and upserts rows")
    void fetchAndSave_mapsInvestorFlow() {
        when(kisInvestorClient.getInvestorFlow("005930")).thenReturn(new KisInvestorResponse(
                "0",
                "OK",
                "success",
                List.of(new KisInvestorResponse.InvestorOutput(
                        "20260610",
                        "302500",
                        "-500000",
                        "300000",
                        "200000",
                        "-151250",
                        "90750",
                        "60500"
                ))
        ));

        int saved = service.fetchAndSave("005930");

        ArgumentCaptor<List<StockSupplyDemandDaily>> captor = ArgumentCaptor.forClass(List.class);
        verify(supplyDemandMapper).insertBatch(captor.capture());
        StockSupplyDemandDaily row = captor.getValue().get(0);

        assertThat(saved).isEqualTo(1);
        assertThat(row.getStockCode()).isEqualTo("005930");
        assertThat(row.getTradeDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(row.getClosePrice()).isEqualByComparingTo(new BigDecimal("302500"));
        assertThat(row.getIndividualNetBuyQty()).isEqualTo(-500000L);
        assertThat(row.getIndividualNetBuyAmount()).isEqualByComparingTo(new BigDecimal("-151250000000"));
        assertThat(row.getForeignNetBuyAmount()).isEqualByComparingTo(new BigDecimal("90750000000"));
        assertThat(row.getInstitutionNetBuyAmount()).isEqualByComparingTo(new BigDecimal("60500000000"));
    }
}
