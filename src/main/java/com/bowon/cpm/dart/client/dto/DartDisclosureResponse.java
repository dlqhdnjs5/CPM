package com.bowon.cpm.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DART 공시 목록 응답
 * API: GET /api/list.json
 *
 * status "000" = 정상, 나머지 = 오류
 */
public record DartDisclosureResponse(
        String status,
        String message,
        List<DisclosureItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public record DisclosureItem(
            /** DART 기업 고유번호 */
            @JsonProperty("corp_code")
            String corpCode,

            /** 기업명 */
            @JsonProperty("corp_name")
            String corpName,

            /** 종목 코드 */
            @JsonProperty("stock_code")
            String stockCode,

            /** 법인 구분: Y=유가증권, K=코스닥, N=코넥스, E=기타 */
            @JsonProperty("corp_cls")
            String corpClass,

            /** 보고서명 */
            @JsonProperty("report_nm")
            String reportName,

            /** 접수번호 (UK) */
            @JsonProperty("rcept_no")
            String receiptNo,

            /** 공시 제출인 */
            @JsonProperty("flr_nm")
            String submitter,

            /** 접수 일자 (yyyyMMdd) */
            @JsonProperty("rcept_dt")
            String receiptDate,

            /** 비고 (유, 연 등) */
            @JsonProperty("rm")
            String remark
    ) {
    }
}

