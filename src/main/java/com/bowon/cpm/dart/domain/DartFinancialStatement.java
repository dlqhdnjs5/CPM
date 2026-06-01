package com.bowon.cpm.dart.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 테이블: dart_financial_statement
 * UK: corp_code + business_year + report_code + statement_type + account_name
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DartFinancialStatement {
    @Setter
    private Long id;
    private String corpCode;
    private String stockCode;
    private Integer businessYear;
    /** 11011=사업보고서, 11012=반기, 11013=1분기, 11014=3분기 */
    private String reportCode;
    /** 재무제표 구분 (BS=재무상태표, IS=손익계산서 등) */
    private String statementType;
    private String accountId;
    private String accountName;
    private BigDecimal amount;
    private String currency;
    /** DART API 원문 응답 JSON */
    private String rawJson;
    private LocalDateTime createdAt;
}


