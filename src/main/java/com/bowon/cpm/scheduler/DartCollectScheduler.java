package com.bowon.cpm.scheduler;

import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.dart.service.DartMajorEventService;
import com.bowon.cpm.dart.service.DartService;
import com.bowon.cpm.fundamental.service.FundamentalIndicatorService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DartCollectScheduler {

    private static final String NAME = "DartCollectScheduler";

    private final SchedulerLogSupport logSupport;
    private final DartService dartService;
    private final DartFinancialService dartFinancialService;
    private final DartMajorEventService dartMajorEventService;
    private final FundamentalIndicatorService fundamentalIndicatorService;
    private final StockMasterMapper stockMasterMapper;

    @Scheduled(cron = "0 45 8 * * MON-FRI")
    public void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) {
            return;
        }

        Long logId = logSupport.start(NAME);
        try {
            List<StockMaster> stocks = stockMasterMapper.findAllActive();
            int disclosureCount = 0;
            int eventCount = 0;
            int financialCount = 0;
            int stockQuantityCount = 0;
            int fundamentalCount = 0;

            for (StockMaster stock : stocks) {
                String stockCode = stock.getStockCode();

                try {
                    dartService.fetchDisclosures(stockCode, LocalDate.now().minusDays(7), LocalDate.now());
                    disclosureCount++;
                } catch (Exception e) {
                    log.warn("[{}] disclosure fetch failed: stockCode={}, error={}", NAME, stockCode, e.getMessage());
                }

                try {
                    dartMajorEventService.classifyAndSave(stockCode);
                    eventCount++;
                } catch (Exception e) {
                    log.warn("[{}] major event classify failed: stockCode={}, error={}", NAME, stockCode, e.getMessage());
                }

                try {
                    dartFinancialService.fetchAndSave(stockCode);
                    financialCount++;
                } catch (Exception e) {
                    log.warn("[{}] financial fetch failed: stockCode={}, error={}", NAME, stockCode, e.getMessage());
                }

                try {
                    dartFinancialService.fetchAndSaveStockQuantity(stockCode);
                    stockQuantityCount++;
                } catch (Exception e) {
                    log.warn("[{}] stock quantity fetch failed: stockCode={}, error={}", NAME, stockCode, e.getMessage());
                }

                try {
                    fundamentalIndicatorService.calculateAndSave(stockCode);
                    fundamentalCount++;
                } catch (Exception e) {
                    log.warn("[{}] fundamental calculate failed: stockCode={}, error={}", NAME, stockCode, e.getMessage());
                }
            }

            logSupport.success(logId, "disclosures=" + disclosureCount
                    + ", events=" + eventCount
                    + ", financials=" + financialCount
                    + ", stockQuantities=" + stockQuantityCount
                    + ", fundamentals=" + fundamentalCount);
        } catch (Exception e) {
            log.error("[{}] failed: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}
