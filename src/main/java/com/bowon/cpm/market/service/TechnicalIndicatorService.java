package com.bowon.cpm.market.service;

import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.mapper.StockIndicatorDailyMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicalIndicatorService {

    private static final int LOOKBACK_DAYS = 260;

    private final TechnicalIndicatorCalculator calculator;
    private final StockMasterMapper stockMasterMapper;
    private final StockPriceDailyMapper stockPriceDailyMapper;
    private final StockIndicatorDailyMapper stockIndicatorDailyMapper;

    @Transactional
    public int calculateAllActive() {
        int saved = 0;
        for (StockMaster stock : stockMasterMapper.findAllActive()) {
            if (calculateForStock(stock.getStockCode()) != null) {
                saved++;
            }
        }
        log.info("[Indicator] active stock indicator calculation completed: saved={}", saved);
        return saved;
    }

    @Transactional
    public int calculateAllActiveForScheduler() {
        int saved = 0;
        for (StockMaster stock : stockMasterMapper.findAllActive()) {
            if (calculateForStock(stock.getStockCode()) != null) {
                saved++;
            }
        }
        log.info("[Indicator] active stock indicator calculation completed: saved={}", saved);
        return saved;
    }

    @Transactional
    public StockIndicatorDaily calculateForStock(String stockCode) {
        List<StockPriceDaily> prices = stockPriceDailyMapper.findByStockCodeAndDateRange(
                stockCode,
                LocalDate.now().minusDays(LOOKBACK_DAYS),
                LocalDate.now()
        );
        StockIndicatorDaily indicator = calculator.calculateLatest(stockCode, prices);
        if (indicator == null) {
            log.warn("[Indicator] skipped: stockCode={}, reason=no daily price", stockCode);
            return null;
        }
        stockIndicatorDailyMapper.upsert(indicator);
        log.info("[Indicator] saved: stockCode={}, tradeDate={}", stockCode, indicator.getTradeDate());
        return indicator;
    }
}
