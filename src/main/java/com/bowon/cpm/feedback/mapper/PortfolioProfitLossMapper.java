package com.bowon.cpm.feedback.mapper;

import com.bowon.cpm.feedback.domain.PortfolioProfitLoss;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PortfolioProfitLossMapper {

    /** INSERT IGNORE — UK: account_no + stock_code + base_date + evaluation_type */
    void insertIgnore(PortfolioProfitLoss profitLoss);

    /** 계좌 + 날짜 + 타입으로 조회 */
    Optional<PortfolioProfitLoss> findByAccountAndDate(
            @Param("accountNo") String accountNo,
            @Param("baseDate") LocalDate baseDate,
            @Param("evaluationType") String evaluationType
    );

    /** 최근 N건 일간 수익률 조회 */
    List<PortfolioProfitLoss> findRecentByAccountNo(
            @Param("accountNo") String accountNo,
            @Param("limit") int limit
    );
}

