package com.bowon.cpm.broker.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisCurrentPriceResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        @JsonProperty("output")
        Output output
) {
    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

    public record Output(
            /** 주식 현재가 */
            @JsonProperty("stck_prpr")
            String currentPrice,

            /** 전일 대비 */
            @JsonProperty("prdy_vrss")
            String changePrice,

            /** 전일 대비율 */
            @JsonProperty("prdy_ctrt")
            String changeRate,

            /** 누적 거래량 */
            @JsonProperty("acml_vol")
            String accumulatedVolume,

            /** 누적 거래대금 */
            @JsonProperty("acml_tr_pbmn")
            String accumulatedTradingValue,

            /** 종목명 */
            @JsonProperty("hts_kor_isnm")
            String stockName
    ) {
    }
}

