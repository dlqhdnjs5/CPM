package com.bowon.cpm.market.mapper;

import com.bowon.cpm.market.domain.StockPriceDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockPriceDailyMapper {

    /**
     * 일봉 배치 저장 — UK(stock_code + trade_date) 중복 시 IGNORE
     */
    void insertBatch(@Param("list") List<StockPriceDaily> list);

    /**
     * 특정 종목의 기간별 일봉 조회
     * 인덱스: idx_stock_price_daily_stock_date_close (stock_code, trade_date)
     */
    List<StockPriceDaily> findByStockCodeAndDateRange(
            @Param("stockCode") String stockCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    StockPriceDaily findLatestByStockCode(@Param("stockCode") String stockCode);

    /**
     * 기간 내 high_price MAX / low_price MIN / 마지막 종가를 한 행으로 반환.
     * 결과는 Map: { high: BigDecimal, low: BigDecimal, last_close: BigDecimal }
     */
    java.util.Map<String, Object> findHighLowInRange(
            @Param("stockCode") String stockCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}

