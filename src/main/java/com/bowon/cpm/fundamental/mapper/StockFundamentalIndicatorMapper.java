package com.bowon.cpm.fundamental.mapper;

import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StockFundamentalIndicatorMapper {

    int upsert(StockFundamentalIndicator indicator);

    Optional<StockFundamentalIndicator> findLatestByStockCode(@Param("stockCode") String stockCode);

    List<StockFundamentalIndicator> findByStockCode(@Param("stockCode") String stockCode,
                                                    @Param("limit") int limit);
}
