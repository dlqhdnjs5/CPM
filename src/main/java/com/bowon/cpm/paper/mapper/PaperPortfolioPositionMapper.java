package com.bowon.cpm.paper.mapper;

import com.bowon.cpm.paper.domain.PaperPortfolioPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PaperPortfolioPositionMapper {
    void upsert(PaperPortfolioPosition position);

    List<PaperPortfolioPosition> findByAccountNo(String accountNo);

    List<PaperPortfolioPosition> findAllHeld(String accountNo);

    Optional<PaperPortfolioPosition> findByAccountNoAndStockCode(
            @Param("accountNo") String accountNo,
            @Param("stockCode") String stockCode
    );
}
