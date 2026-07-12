package com.bowon.cpm.broker.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * KIS investor flow response.
 *
 * API: GET /uapi/domestic-stock/v1/quotations/inquire-investor
 * TR_ID: FHKST01010900
 */
public record KisInvestorResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        @JsonProperty("output")
        List<InvestorOutput> output
) {
    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

    public record InvestorOutput(
            @JsonProperty("stck_bsop_date")
            String tradeDate,

            @JsonProperty("stck_clpr")
            String closePrice,

            @JsonProperty("prsn_ntby_qty")
            String individualNetBuyQty,

            @JsonProperty("frgn_ntby_qty")
            String foreignNetBuyQty,

            @JsonProperty("orgn_ntby_qty")
            String institutionNetBuyQty,

            @JsonProperty("prsn_ntby_tr_pbmn")
            String individualNetBuyAmount,

            @JsonProperty("frgn_ntby_tr_pbmn")
            String foreignNetBuyAmount,

            @JsonProperty("orgn_ntby_tr_pbmn")
            String institutionNetBuyAmount
    ) {
    }
}
