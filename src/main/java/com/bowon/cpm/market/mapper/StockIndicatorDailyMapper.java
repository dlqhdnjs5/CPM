package com.bowon.cpm.market.mapper;

import com.bowon.cpm.market.domain.StockIndicatorDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface StockIndicatorDailyMapper {

    /** 종목별 최신 기술적 지표 1건 (없으면 empty) */
    Optional<StockIndicatorDaily> findLatestByStockCode(@Param("stockCode") String stockCode);
}

