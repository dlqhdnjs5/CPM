package com.bowon.cpm.scheduler;

import com.bowon.cpm.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Plan 14 Phase 4: MONTHLY 피드백
 * - 매월 1일 오전 06:00
 * - 전월(1일 ~ 말일) 통계 → ai_periodic_summary (summary_type='MONTHLY') 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyFeedbackScheduler {

    private static final String NAME = "MonthlyFeedbackScheduler";
    private final SchedulerLogSupport logSupport;
    private final FeedbackService feedbackService;

    @Scheduled(cron = "0 0 6 1 * *")
    public void run() {
        if (logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            LocalDate today = LocalDate.now();
            LocalDate first = today.minusMonths(1).withDayOfMonth(1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            feedbackService.evaluateMonthly(first, last);
            logSupport.success(logId, "MONTHLY 요약 저장 " + first + " ~ " + last);
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

