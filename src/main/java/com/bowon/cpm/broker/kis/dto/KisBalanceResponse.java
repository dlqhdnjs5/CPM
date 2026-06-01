package com.bowon.cpm.broker.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KisBalanceResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        /** 보유 종목 목록 */
        @JsonProperty("output1")
        List<PositionOutput> positions,

        /** 계좌 잔고 요약 */
        @JsonProperty("output2")
        List<BalanceOutput> balances
) {
    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

    public record PositionOutput(
            /** 종목코드 */
            @JsonProperty("pdno")
            String stockCode,

            /** 종목명 */
            @JsonProperty("prdt_name")
            String stockName,

            /** 보유 수량 */
            @JsonProperty("hldg_qty")
            String quantity,

            /** 매도 가능 수량 */
            @JsonProperty("ord_psbl_qty")
            String availableQuantity,

            /** 매입 평균가 */
            @JsonProperty("pchs_avg_pric")
            String averageBuyPrice,

            /** 현재가 */
            @JsonProperty("prpr")
            String currentPrice,

            /** 매입 금액 */
            @JsonProperty("pchs_amt")
            String purchaseAmount,

            /** 평가 금액 */
            @JsonProperty("evlu_amt")
            String valuationAmount,

            /** 평가 손익 금액 */
            @JsonProperty("evlu_pfls_amt")
            String profitLossAmount,

            /** 평가 손익률 */
            @JsonProperty("evlu_pfls_rt")
            String profitLossRate
    ) {
    }

    public record BalanceOutput(
            /** 예수금 총금액 */
            @JsonProperty("dnca_tot_amt")
            String cashBalance,

            /** 주문 가능 현금 */
            @JsonProperty("nxdy_excc_amt")
            String availableCash,

            /** 순자산 금액 */
            @JsonProperty("nass_amt")
            String totalAssetAmount,

            /** 유가증권 평가 금액 */
            @JsonProperty("scts_evlu_amt")
            String stockEvaluationAmount,

            /** 총 평가 손익 금액 */
            @JsonProperty("evlu_pfls_smtl_amt")
            String totalProfitLossAmount,

            /** 총 수익률 */
            @JsonProperty("asst_icdc_erng_rt")
            String totalProfitLossRate
    ) {
    }
}

