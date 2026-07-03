package com.bowon.cpm.scheduler;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.service.AiDecisionService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 판단 스케줄러
 * - 장중 매 30분 (09:30, 10:00, ..., 15:00)
 * - 각 active 종목에 대해 AI 판단 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiDecisionScheduler {

    private static final String NAME = "AiDecisionScheduler";
    private final SchedulerLogSupport logSupport;
    private final AiDecisionService aiDecisionService;
    private final StockMasterMapper stockMasterMapper;

    @Scheduled(cron = "0 30 9 * * MON-FRI")
    public void run0930() { run(); }

    @Scheduled(cron = "0 0 10-14 * * MON-FRI")
    public void runHourly() { run(); }

    @Scheduled(cron = "0 30 10-14 * * MON-FRI")
    public void runHalfHourly() { run(); }

    @Scheduled(cron = "0 0 15 * * MON-FRI")
    public void run1500() { run(); }

    private void run() {
        if (!logSupport.isMarketOpen() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            List<StockMaster> stocks = stockMasterMapper.findAllActive();
            int buyCount = 0, holdCount = 0, failCount = 0;
            for (StockMaster stock : stocks) {
                try {
                    AiDecision decision = aiDecisionService.generateDecision(stock.getStockCode());
                    if (decision != null && "BUY".equals(decision.getDecision())) buyCount++;
                    else holdCount++;
                } catch (Exception e) {
                    failCount++;
                    log.warn("[{}] {} AI 판단 실패: {}", NAME, stock.getStockCode(), e.getMessage());
                }
            }
            logSupport.success(logId, "BUY:" + buyCount + ", HOLD:" + holdCount + ", FAIL:" + failCount);
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

