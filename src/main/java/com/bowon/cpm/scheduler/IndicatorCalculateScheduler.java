package com.bowon.cpm.scheduler;

import com.bowon.cpm.market.service.TechnicalIndicatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorCalculateScheduler {

    private static final String NAME = "IndicatorCalculateScheduler";

    private final SchedulerLogSupport logSupport;
    private final TechnicalIndicatorService technicalIndicatorService;

    @Scheduled(cron = "0 50 8 * * MON-FRI")
    public void runBeforeMarket() {
        run();
    }

    @Scheduled(cron = "0 20 16 * * MON-FRI")
    public void runAfterClose() {
        run();
    }

    private void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) {
            return;
        }
        Long logId = logSupport.start(NAME);
        try {
            int saved = technicalIndicatorService.calculateAllActive();
            logSupport.success(logId, "indicator saved=" + saved);
        } catch (Exception e) {
            log.error("[{}] failed: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}
