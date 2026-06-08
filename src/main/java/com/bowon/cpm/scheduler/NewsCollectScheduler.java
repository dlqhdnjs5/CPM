package com.bowon.cpm.scheduler;

import com.bowon.cpm.news.service.NewsCollectService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 뉴스 수집 스케줄러
 * - 08:40 장 시작 전: 아침 뉴스 반영
 * - 16:10 장 마감 후: 오후 뉴스 반영
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCollectScheduler {

    private static final String NAME = "NewsCollectScheduler";
    private final SchedulerLogSupport logSupport;
    private final NewsCollectService newsCollectService;
    private final StockMasterMapper stockMasterMapper;

    @Scheduled(cron = "0 40 8 * * MON-FRI")
    public void collectMorning() { run(); }

    @Scheduled(cron = "0 10 16 * * MON-FRI")
    public void collectAfternoon() { run(); }

    private void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            List<StockMaster> stocks = stockMasterMapper.findAllWatched();
            int count = 0;
            for (StockMaster stock : stocks) {
                try {
                    newsCollectService.collectNews(stock.getStockCode(), stock.getStockName(), 10);
                    count++;
                } catch (Exception e) {
                    log.warn("[{}] {} 뉴스 수집 실패: {}", NAME, stock.getStockCode(), e.getMessage());
                }
            }
            logSupport.success(logId, count + "종목 뉴스 수집 완료");
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}


