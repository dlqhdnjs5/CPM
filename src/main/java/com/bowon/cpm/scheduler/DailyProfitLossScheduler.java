package com.bowon.cpm.scheduler;

import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일간 포트폴리오 수익률 스케줄러
 * - 16:45 장 마감 후: 오늘 수익률 계산 → portfolio_profit_loss 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyProfitLossScheduler {

    private static final String NAME = "DailyProfitLossScheduler";
    private final SchedulerLogSupport logSupport;
    private final FeedbackService feedbackService;
    private final TradingProperties tradingProperties;

    @Scheduled(cron = "0 45 16 * * MON-FRI")
    public void run() {
        if (tradingProperties.isPaperMode()) {
            log.debug("[{}] skipped in PAPER mode", NAME);
            return;
        }
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            var result = feedbackService.saveDailyProfitLoss();
            logSupport.success(logId, "수익률 저장: baseDate=" + result.getBaseDate()
                    + ", returnRate=" + result.getReturnRate());
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

