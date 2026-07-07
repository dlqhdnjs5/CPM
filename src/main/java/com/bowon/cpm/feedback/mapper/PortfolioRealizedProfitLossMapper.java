package com.bowon.cpm.feedback.mapper;

import com.bowon.cpm.feedback.domain.PortfolioRealizedProfitLoss;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface PortfolioRealizedProfitLossMapper {
    void insertIgnore(PortfolioRealizedProfitLoss profitLoss);

    Optional<PortfolioRealizedProfitLoss> findByOrderExecutionId(
            @Param("orderExecutionId") Long orderExecutionId
    );

    List<PortfolioRealizedProfitLoss> findRecentPaperByAccountNo(
            @Param("accountNo") String accountNo,
            @Param("limit") int limit
    );

    Map<String, Object> aggregatePaperByAccountNo(@Param("accountNo") String accountNo);

    int countRecentLossesByStock(
            @Param("accountNo") String accountNo,
            @Param("stockCode") String stockCode,
            @Param("paperMode") boolean paperMode,
            @Param("since") LocalDateTime since
    );

    int countRecentLossesByAccount(
            @Param("accountNo") String accountNo,
            @Param("paperMode") boolean paperMode,
            @Param("since") LocalDateTime since
    );
}
