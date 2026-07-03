package com.bowon.cpm.scheduler;

import com.bowon.cpm.market.service.SupplyDemandService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyDemandSyncSchedulerTest {

    @Mock SchedulerLogSupport logSupport;
    @Mock SupplyDemandService supplyDemandService;
    @Mock StockMasterMapper stockMasterMapper;
    @InjectMocks SupplyDemandSyncScheduler scheduler;

    @Test
    @DisplayName("scheduler fetches supply demand for active stocks")
    void run_fetchesActiveStocks() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(false);
        when(logSupport.start(anyString())).thenReturn(10L);
        when(stockMasterMapper.findAllActive()).thenReturn(List.of(
                StockMaster.builder().stockCode("005930").build(),
                StockMaster.builder().stockCode("042700").build()
        ));
        when(supplyDemandService.fetchAndSave("005930")).thenReturn(3);
        when(supplyDemandService.fetchAndSave("042700")).thenReturn(2);

        scheduler.run();

        verify(stockMasterMapper).findAllActive();
        verify(supplyDemandService).fetchAndSave("005930");
        verify(supplyDemandService).fetchAndSave("042700");
        verify(logSupport).success(eq(10L), anyString());
    }

    @Test
    @DisplayName("scheduler skips when already running")
    void run_skipsWhenAlreadyRunning() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(true);

        scheduler.run();

        verify(stockMasterMapper, never()).findAllActive();
        verify(supplyDemandService, never()).fetchAndSave(anyString());
    }
}
