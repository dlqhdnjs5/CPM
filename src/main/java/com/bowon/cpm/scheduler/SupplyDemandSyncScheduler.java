package com.bowon.cpm.scheduler;

import com.bowon.cpm.market.service.SupplyDemandService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplyDemandSyncScheduler {

    private static final String NAME = "SupplyDemandSyncScheduler";

    private final SchedulerLogSupport logSupport;
    private final SupplyDemandService supplyDemandService;
    private final StockMasterMapper stockMasterMapper;

    @Scheduled(cron = "0 10 17 * * MON-FRI")
    public void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) {
            return;
        }

        Long logId = logSupport.start(NAME);
        try {
            List<StockMaster> stocks = stockMasterMapper.findAllActive();
            int successCount = 0;
            int failCount = 0;
            int savedCount = 0;
            for (StockMaster stock : stocks) {
                try {
                    savedCount += supplyDemandService.fetchAndSave(stock.getStockCode());
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.warn("[{}] failed stockCode={}, error={}", NAME, stock.getStockCode(), e.getMessage());
                }
            }
            logSupport.success(logId, "stocks=" + successCount
                    + ", saved=" + savedCount + ", failed=" + failCount);
        } catch (Exception e) {
            log.error("[{}] failed: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}
