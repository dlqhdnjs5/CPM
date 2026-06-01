package com.bowon.cpm.scheduler;

import com.bowon.cpm.dart.service.DartService;
import com.bowon.cpm.dart.service.DartMajorEventService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * DART 공시/재무/이벤트 수집 스케줄러
 * - 08:45 장 시작 전: 전날 밤 공시 반영
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartCollectScheduler {

    private static final String NAME = "DartCollectScheduler";
    private final SchedulerLogSupport logSupport;
    private final DartService dartService;
    private final DartMajorEventService dartMajorEventService;
    private final StockMasterMapper stockMasterMapper;

    @Scheduled(cron = "0 45 8 * * MON-FRI")
    public void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            List<StockMaster> stocks = stockMasterMapper.findAllActive();
            int disclosureCount = 0;
            int eventCount = 0;
            for (StockMaster stock : stocks) {
                try {
                    dartService.fetchDisclosures(stock.getStockCode(),
                            LocalDate.now().minusDays(7), LocalDate.now());
                    disclosureCount++;
                } catch (Exception e) {
                    log.warn("[{}] {} 공시 수집 실패: {}", NAME, stock.getStockCode(), e.getMessage());
                }
                try {
                    dartMajorEventService.classifyAndSave(stock.getStockCode());
                    eventCount++;
                } catch (Exception e) {
                    log.warn("[{}] {} 이벤트 분류 실패: {}", NAME, stock.getStockCode(), e.getMessage());
                }
            }
            logSupport.success(logId, "공시:" + disclosureCount + "종목, 이벤트:" + eventCount + "종목");
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}





