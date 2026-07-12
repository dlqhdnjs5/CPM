package com.bowon.cpm.portfolio.mapper;

import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PortfolioPositionMapper {
    /**
     * 보유 종목 UPSERT — 존재하면 UPDATE, 없으면 INSERT
     */
    void upsert(PortfolioPosition position);

    List<PortfolioPosition> findByAccountNo(String accountNo);

    List<PortfolioPosition> findAllHeld(String accountNo);

    /** 계좌번호 + 종목코드로 단건 조회 */
    Optional<PortfolioPosition> findByAccountNoAndStockCode(
            @Param("accountNo") String accountNo,
            @Param("stockCode") String stockCode
    );
}
