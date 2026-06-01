package com.bowon.cpm.dart.mapper;

import com.bowon.cpm.dart.domain.DartFinancialStatement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DartFinancialStatementMapper {

    /** INSERT IGNORE — UK: corp_code+business_year+report_code+statement_type+account_name */
    void insertIgnore(DartFinancialStatement statement);

    /** 종목코드 + 연도별 재무제표 조회 (최신 보고서 우선) */
    List<DartFinancialStatement> findByStockCode(
            @Param("stockCode") String stockCode,
            @Param("limit") int limit
    );

    /** corp_code + 연도 + 보고서 코드로 조회 */
    List<DartFinancialStatement> findByCorpCodeAndYear(
            @Param("corpCode") String corpCode,
            @Param("businessYear") int businessYear,
            @Param("reportCode") String reportCode
    );
}

