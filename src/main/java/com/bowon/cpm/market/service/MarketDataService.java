package com.bowon.cpm.market.service;

import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisDailyPriceClient;
import com.bowon.cpm.broker.kis.dto.KisDailyPriceResponse;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.domain.StockRealtimeQuote;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.market.mapper.StockRealtimeQuoteMapper;
import com.bowon.cpm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final BrokerClient brokerClient;
    private final KisDailyPriceClient kisDailyPriceClient;
    private final StockRealtimeQuoteMapper realtimeQuoteMapper;
    private final StockPriceDailyMapper stockPriceDailyMapper;
    private final BrokerApiLogMapper brokerApiLogMapper;
    private final StockService stockService;
    private final Clock clock;

    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final LocalTime DAILY_CANDLE_AVAILABLE_TIME = LocalTime.of(15, 40);
    private static final String UNKNOWN_MARKET_TYPE = "UNKNOWN";

    /**
     * 현재가 조회 후 stock_realtime_quote 저장
     */
    @Transactional
    public StockQuoteResult fetchAndSaveCurrentPrice(String stockCode) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        StockQuoteResult result;

        try {
            result = brokerClient.getCurrentPrice(stockCode);
            success = true;

            realtimeQuoteMapper.insert(StockRealtimeQuote.builder()
                    .stockCode(stockCode)
                    .quoteTime(result.getQuoteTime())
                    .currentPrice(result.getCurrentPrice())
                    .changePrice(result.getChangePrice())
                    .changeRate(result.getChangeRate())
                    .accumulatedVolume(result.getAccumulatedVolume())
                    .tradingValue(result.getTradingValue())
                    .build());
            upsertStockMasterIfNamePresent(stockCode, result.getStockName());

            log.info("[Market] 현재가 저장 완료: stockCode={}, price={}", stockCode, result.getCurrentPrice());

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[Market] 현재가 조회 실패: stockCode={}, error={}", stockCode, errorMessage);
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            saveBrokerApiLog("현재가조회",
                    "/uapi/domestic-stock/v1/quotations/inquire-price?stockCode=" + stockCode,
                    success, errorMessage, elapsed);
        }

        return result;
    }

    /**
     * 일봉 조회 후 stock_price_daily + stock_master 저장
     *
     * @param stockCode 종목 코드 (예: "005930")
     * @return 저장된 일봉 수
     */
    @Transactional
    public int fetchAndSaveDailyPrices(String stockCode) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        int savedCount = 0;

        try {
            KisDailyPriceResponse response = kisDailyPriceClient.getDailyPrice(stockCode, "D");

            if (response.output2() == null || response.output2().isEmpty()) {
                log.warn("[Market] 일봉 조회 결과 없음: stockCode={}", stockCode);
                return 0;
            }

            // KIS 응답 → StockPriceDaily 변환
            List<StockPriceDaily> dailyList = response.output2().stream()
                    .filter(o -> o.tradeDate() != null && !o.tradeDate().isBlank())
                    .map(o -> StockPriceDaily.builder()
                            .stockCode(stockCode)
                            .tradeDate(LocalDate.parse(o.tradeDate(), KIS_DATE_FORMAT))
                            .openPrice(parseBigDecimal(o.openPrice()))
                            .highPrice(parseBigDecimal(o.highPrice()))
                            .lowPrice(parseBigDecimal(o.lowPrice()))
                            .closePrice(parseBigDecimal(o.closePrice()))
                            .volume(parseLong(o.volume()))
                            .tradingValue(parseBigDecimal(o.tradingValue()))
                            .source("KIS")
                            .build())
                    .filter(this::isConfirmedDailyPrice)
                    .collect(Collectors.toList());

            // Upsert so an early partial daily candle can be corrected by a later fetch.
            if (!dailyList.isEmpty()) {
                stockPriceDailyMapper.insertBatch(dailyList);
                savedCount = dailyList.size();
            }

            // stock_master upsert (종목명은 현재가 응답에서 가져오지 못하므로 코드만 저장)
            stockService.upsertStockMaster(stockCode, resolveStockName(stockCode), UNKNOWN_MARKET_TYPE);

            success = true;
            log.info("[Market] 일봉 저장 완료: stockCode={}, count={}", stockCode, savedCount);

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[Market] 일봉 조회 실패: stockCode={}, error={}", stockCode, errorMessage);
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            saveBrokerApiLog("일봉조회",
                    "/uapi/domestic-stock/v1/quotations/inquire-daily-price?stockCode=" + stockCode,
                    success, errorMessage, elapsed);
        }

        return savedCount;
    }

    /**
     * 저장된 일봉 조회
     */
    @Transactional(readOnly = true)
    public List<StockPriceDaily> getDailyPrices(String stockCode, LocalDate fromDate, LocalDate toDate) {
        return stockPriceDailyMapper.findByStockCodeAndDateRange(stockCode, fromDate, toDate);
    }

    private void saveBrokerApiLog(String apiName, String requestUrl, boolean success,
                                   String errorMessage, long elapsedMs) {
        try {
            brokerApiLogMapper.insert(BrokerApiLog.builder()
                    .brokerType("KIS")
                    .apiName(apiName)
                    .httpMethod("GET")
                    .requestUrl(requestUrl)
                    .success(success)
                    .errorMessage(errorMessage)
                    .elapsedMs(elapsedMs)
                    .calledAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[BrokerApiLog] 로그 저장 실패: {}", e.getMessage());
        }
    }

    private void upsertStockMasterIfNamePresent(String stockCode, String stockName) {
        if (hasUsableStockName(stockName, stockCode)) {
            stockService.upsertStockMaster(stockCode, stockName, UNKNOWN_MARKET_TYPE);
        }
    }

    private String resolveStockName(String stockCode) {
        try {
            StockQuoteResult quote = brokerClient.getCurrentPrice(stockCode);
            if (quote != null && hasUsableStockName(quote.getStockName(), stockCode)) {
                return quote.getStockName();
            }
        } catch (Exception e) {
            log.warn("[Market] stock name lookup failed: stockCode={}, error={}", stockCode, e.getMessage());
        }
        return stockService.findByStockCode(stockCode)
                .map(stock -> stock.getStockName())
                .filter(name -> hasUsableStockName(name, stockCode))
                .orElse(stockCode);
    }

    private boolean hasUsableStockName(String stockName, String stockCode) {
        return stockName != null && !stockName.isBlank() && !stockName.equals(stockCode);
    }

    private boolean isConfirmedDailyPrice(StockPriceDaily dailyPrice) {
        LocalDate tradeDate = dailyPrice.getTradeDate();
        if (tradeDate == null) {
            return false;
        }

        LocalDate today = LocalDate.now(clock);
        if (tradeDate.isAfter(today)) {
            return false;
        }
        return !tradeDate.isEqual(today) || !LocalTime.now(clock).isBefore(DAILY_CANDLE_AVAILABLE_TIME);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
