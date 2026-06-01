package com.bowon.cpm.broker.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * KIS 주식 일자별 시세 응답
 *
 * API: GET /uapi/domestic-stock/v1/quotations/inquire-daily-price
 * TR_ID: FHKST01010400 (실전투자)
 */
public record KisDailyPriceResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        /**
         * 일자별 시세 목록 (최근일 기준 내림차순)
         * KIS inquire-daily-price 응답 필드명: "output" (output2 아님)
         */
        @JsonProperty("output")
        List<DailyOutput> output2
) {
    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

    public record DailyOutput(
            /** 영업 일자 (yyyyMMdd) */
            @JsonProperty("stck_bsop_date")
            String tradeDate,

            /** 시가 */
            @JsonProperty("stck_oprc")
            String openPrice,

            /** 고가 */
            @JsonProperty("stck_hgpr")
            String highPrice,

            /** 저가 */
            @JsonProperty("stck_lwpr")
            String lowPrice,

            /** 종가 */
            @JsonProperty("stck_clpr")
            String closePrice,

            /** 누적 거래량 */
            @JsonProperty("acml_vol")
            String volume,

            /** 누적 거래대금 */
            @JsonProperty("acml_tr_pbmn")
            String tradingValue
    ) {
    }
}


