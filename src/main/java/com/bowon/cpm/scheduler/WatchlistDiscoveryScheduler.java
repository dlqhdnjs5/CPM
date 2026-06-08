package com.bowon.cpm.scheduler;

import com.bowon.cpm.watchlist.service.WatchlistDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistDiscoveryScheduler {

    private static final String NAME = "WatchlistDiscoveryScheduler";
    private static final int NEWS_ANALYZE_LIMIT = 20;

    private final SchedulerLogSupport logSupport;
    private final WatchlistDiscoveryService watchlistDiscoveryService;

    @Scheduled(cron = "0 15 8 * * MON-FRI")
    public void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) {
            return;
        }

        Long logId = logSupport.start(NAME);
        try {
            WatchlistDiscoveryService.DiscoveryResult result =
                    watchlistDiscoveryService.discover(true, NEWS_ANALYZE_LIMIT);
            logSupport.success(logId, "scored=" + result.scoredCount()
                    + ", selected=" + result.selectedStocks().size()
                    + ", watched=" + result.watchedAfter());
        } catch (Exception e) {
            log.error("[{}] failed: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}
