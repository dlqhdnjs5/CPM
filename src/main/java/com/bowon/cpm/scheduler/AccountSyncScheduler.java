package com.bowon.cpm.scheduler;

import com.bowon.cpm.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 계좌 잔고 동기화 스케줄러
 * - 08:30 장 시작 전: 오늘 시작 잔고 기록
 * - 15:35 장 마감 후: 종료 잔고 기록
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSyncScheduler {

    private static final String NAME = "AccountSyncScheduler";
    private final SchedulerLogSupport logSupport;
    private final PortfolioService portfolioService;

    @Scheduled(cron = "0 30 8 * * MON-FRI")
    public void syncMorning() { run(); }

    @Scheduled(cron = "0 35 15 * * MON-FRI")
    public void syncAfterClose() { run(); }

    private void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            portfolioService.syncAccountBalance();
            logSupport.success(logId, "계좌 동기화 완료");
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

