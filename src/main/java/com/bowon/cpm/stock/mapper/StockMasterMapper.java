package com.bowon.cpm.stock.mapper;

import com.bowon.cpm.stock.domain.StockMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StockMasterMapper {

    /** 종목 없으면 INSERT, 있으면 종목명 등 업데이트 */
    void upsert(StockMaster stockMaster);

    Optional<StockMaster> findByStockCode(String stockCode);

    /** 활성 종목 전체 조회 */
    List<StockMaster> findAllActive();

    List<StockMaster> findAllWatched();

    int countWatched();

    int updateWatched(@Param("stockCode") String stockCode, @Param("isWatched") boolean isWatched);

    int updateMarketType(@Param("stockCode") String stockCode, @Param("marketType") String marketType);

    int markHeldPositionsWatched(@Param("accountNo") String accountNo);

    int unwatchOverflowNonHeld(@Param("accountNo") String accountNo, @Param("limit") int limit);
}

