package com.bowon.cpm.scheduler;

import com.bowon.cpm.portfolio.service.AccountRevaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountRevaluationScheduler {

    private static final String NAME = "AccountRevaluationScheduler";

    private final SchedulerLogSupport logSupport;
    private final AccountRevaluationService accountRevaluationService;

    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    public void run() {
        if (!logSupport.isMarketOpen() || logSupport.isAlreadyRunning(NAME)) {
            return;
        }
        Long logId = logSupport.start(NAME);
        try {
            Map<String, Object> result = accountRevaluationService.revalueCurrentMode();
            logSupport.success(logId, "account revalued: mode=" + result.get("mode"));
        } catch (Exception e) {
            log.error("[{}] failed: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}
