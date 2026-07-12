package com.bowon.cpm.scheduler;

import com.bowon.cpm.macro.service.MacroNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MacroNewsCollectScheduler {

    private static final String NAME = "MacroNewsCollectScheduler";
    private static final int MORNING_DISPLAY_PER_KEYWORD = 3;
    private static final int INTRADAY_DISPLAY_PER_KEYWORD = 2;
    private static final int ANALYZE_LIMIT_WHEN_DISABLED = 0;

    private final SchedulerLogSupport logSupport;
    private final MacroNewsService macroNewsService;

    @Scheduled(cron = "0 10 6 * * MON-FRI")
    public void collectEarlyMorning() {
        run(MORNING_DISPLAY_PER_KEYWORD);
    }

    @Scheduled(cron = "0 0 15 * * MON-FRI")
    public void collectIntraday() {
        run(INTRADAY_DISPLAY_PER_KEYWORD);
    }

    void run(int displayPerKeyword) {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) {
            return;
        }

        Long logId = logSupport.start(NAME);
        try {
            MacroNewsService.MacroCollectResult result = macroNewsService.collectDefault(
                    displayPerKeyword,
                    false,
                    ANALYZE_LIMIT_WHEN_DISABLED
            );
            logSupport.success(logId, "macro news saved=" + result.totalSaved()
                    + ", displayPerKeyword=" + displayPerKeyword);
        } catch (Exception e) {
            log.error("[{}] failed: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}
