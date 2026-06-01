package com.bowon.cpm.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DART 단일회사 주요계정 재무제표 응답
 * API: /api/fnlttSinglAcnt.json
 */
public record DartFinancialResponse(
        String status,
        String message,
        List<FinancialItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public boolean isEmpty() {
        return "100".equals(status);
    }

    public record FinancialItem(
            @JsonProperty("rcept_no")      String receiptNo,
            @JsonProperty("reprt_code")    String reportCode,
            @JsonProperty("bsns_year")     String businessYear,
            @JsonProperty("corp_code")     String corpCode,
            @JsonProperty("stock_code")    String stockCode,
            /** CFS=연결, OFS=별도 */
            @JsonProperty("fs_div")        String fsDiv,
            @JsonProperty("fs_nm")         String fsName,
            /** BS=재무상태표, IS=손익계산서, CF=현금흐름표 */
            @JsonProperty("sj_div")        String statementDiv,
            @JsonProperty("sj_nm")         String statementName,
            @JsonProperty("account_id")    String accountId,
            @JsonProperty("account_nm")    String accountName,
            /** 당기 금액 */
            @JsonProperty("thstrm_amount") String currentAmount,
            /** 전기 금액 */
            @JsonProperty("frmtrm_amount") String previousAmount,
            @JsonProperty("currency")      String currency
    ) {
    }
}

