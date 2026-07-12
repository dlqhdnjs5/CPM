package com.bowon.cpm.scheduler;

import com.bowon.cpm.news.service.NewsAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsAnalysisScheduler {

    private static final String NAME = "NewsAnalysisScheduler";
    private static final int LIMIT = 50;

    private final SchedulerLogSupport logSupport;
    private final NewsAnalysisService newsAnalysisService;

    @Scheduled(cron = "0 30 6 * * MON-FRI")
    public void runEarlyMorning() {
        run();
    }

    @Scheduled(cron = "0 55 8 * * MON-FRI")
    public void runBeforeMarket() {
        run();
    }

    @Scheduled(cron = "0 25 16 * * MON-FRI")
    public void runAfterClose() {
        run();
    }

    private void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) {
            return;
        }
        Long logId = logSupport.start(NAME);
        try {
            NewsAnalysisService.AnalysisBatchResult result = newsAnalysisService.analyzePending(LIMIT);
            logSupport.success(logId, "news analysis saved=" + result.saved()
                    + ", failed=" + result.failed());
        } catch (Exception e) {
            log.error("[{}] failed: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}
