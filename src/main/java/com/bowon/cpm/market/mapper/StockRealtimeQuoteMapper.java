package com.bowon.cpm.market.mapper;

import com.bowon.cpm.market.domain.StockRealtimeQuote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockRealtimeQuoteMapper {
    void insert(StockRealtimeQuote quote);
}

