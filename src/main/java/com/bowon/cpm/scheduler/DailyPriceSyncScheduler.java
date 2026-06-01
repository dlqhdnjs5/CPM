package com.bowon.cpm.scheduler;

import com.bowon.cpm.market.service.MarketDataService;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 일봉 수집 스케줄러
 * - 08:35 장 시작 전: 전일 종가 포함 최근 30일 일봉 수집
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPriceSyncScheduler {

    private static final String NAME = "DailyPriceSyncScheduler";
    private final SchedulerLogSupport logSupport;
    private final MarketDataService marketDataService;
    private final StockMasterMapper stockMasterMapper;

    @Scheduled(cron = "0 35 8 * * MON-FRI")
    public void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            List<StockMaster> stocks = stockMasterMapper.findAllActive();
            int total = 0;
            for (StockMaster stock : stocks) {
                try {
                    total += marketDataService.fetchAndSaveDailyPrices(stock.getStockCode());
                } catch (Exception e) {
                    log.warn("[{}] {} 실패: {}", NAME, stock.getStockCode(), e.getMessage());
                }
            }
            logSupport.success(logId, stocks.size() + "종목, " + total + "건 저장");
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

