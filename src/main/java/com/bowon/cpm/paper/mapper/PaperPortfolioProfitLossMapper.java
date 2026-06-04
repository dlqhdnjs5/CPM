package com.bowon.cpm.paper.mapper;

import com.bowon.cpm.paper.domain.PaperPortfolioProfitLoss;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaperPortfolioProfitLossMapper {
    void upsert(PaperPortfolioProfitLoss profitLoss);

    List<PaperPortfolioProfitLoss> findRecentByAccountNo(
            @Param("accountNo") String accountNo,
            @Param("limit") int limit
    );
}
