package com.bowon.cpm.market.mapper;

import com.bowon.cpm.market.domain.StockSupplyDemandDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StockSupplyDemandDailyMapper {

    void insertBatch(@Param("list") List<StockSupplyDemandDaily> list);

    Optional<StockSupplyDemandDaily> findLatestByStockCode(@Param("stockCode") String stockCode);

    List<StockSupplyDemandDaily> findByStockCode(
            @Param("stockCode") String stockCode,
            @Param("limit") int limit
    );
}
