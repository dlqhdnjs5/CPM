package com.bowon.cpm.scheduler;

import com.bowon.cpm.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Plan 14 Phase 3: WEEKLY 피드백
 * - 매주 금요일 장 마감 후 17:30
 * - 이번 주(월~금) 통계 → ai_periodic_summary (summary_type='WEEKLY') 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyFeedbackScheduler {

    private static final String NAME = "WeeklyFeedbackScheduler";
    private final SchedulerLogSupport logSupport;
    private final FeedbackService feedbackService;

    @Scheduled(cron = "0 30 17 * * FRI")
    public void run() {
        if (logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            LocalDate today = LocalDate.now();
            LocalDate weekStart = today.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(4);
            feedbackService.evaluateWeekly(weekStart, weekEnd);
            logSupport.success(logId, "WEEKLY 요약 저장 " + weekStart + " ~ " + weekEnd);
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

