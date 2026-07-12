package com.bowon.cpm.scheduler;

import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.service.ExecutionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 체결 동기화 스케줄러
 * - 장중 매 10분: 체결 내역 KIS에서 가져와 DB 저장
 * - 16:35 장 마감 후 최종 동기화 (정산 완료 후)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionSyncScheduler {

    private static final String NAME = "ExecutionSyncScheduler";
    private final SchedulerLogSupport logSupport;
    private final ExecutionSyncService executionSyncService;
    private final TradingProperties tradingProperties;

    @Scheduled(cron = "0 */10 9-15 * * MON-FRI")
    public void runDuringMarket() { run(); }

    /** 장 마감 후 최종 동기화 (16:35 — 정산 시간 이후) */
    @Scheduled(cron = "0 35 16 * * MON-FRI")
    public void runFinal() { run(); }

    private void run() {
        if (tradingProperties.isPaperMode()) {
            log.debug("[{}] skipped in PAPER mode", NAME);
            return;
        }
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            int count = executionSyncService.syncExecutions();
            logSupport.success(logId, "체결 동기화: " + count + "건");
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

