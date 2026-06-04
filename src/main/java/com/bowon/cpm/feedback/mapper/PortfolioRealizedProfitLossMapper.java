package com.bowon.cpm.feedback.mapper;

import com.bowon.cpm.feedback.domain.PortfolioRealizedProfitLoss;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface PortfolioRealizedProfitLossMapper {
    void insertIgnore(PortfolioRealizedProfitLoss profitLoss);

    Optional<PortfolioRealizedProfitLoss> findByOrderExecutionId(
            @Param("orderExecutionId") Long orderExecutionId
    );
}
