package com.bowon.cpm.news.mapper;

import com.bowon.cpm.news.domain.StockNews;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}

