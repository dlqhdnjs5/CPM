package com.bowon.cpm.news.mapper;

import com.bowon.cpm.news.domain.StockNews;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StockNewsMapper {

    /** INSERT IGNORE — UK: origin_url_hash */
    void insertIgnore(StockNews news);

    /** 종목별 뉴스 최신순 조회 */
    List<StockNews> findByStockCode(
            @Param("stockCode") String stockCode,
            @Param("limit") int limit
    );

    /** 종목별 + 발행일 since 이후 뉴스 최신순 조회 (AI 판단용) */
    List<StockNews> findByStockCodeAndPublishedAfter(
            @Param("stockCode") String stockCode,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );

    List<StockNews> findPendingAnalysisTargets(@Param("limit") int limit);
}

