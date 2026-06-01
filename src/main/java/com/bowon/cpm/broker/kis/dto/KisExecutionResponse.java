package com.bowon.cpm.broker.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * KIS 당일 체결 내역 응답 DTO
 * API: GET /uapi/domestic-stock/v1/trading/inquire-daily-ccld
 * TR ID: TTTC8001R (실전)
 */
public record KisExecutionResponse(
        @JsonProperty("rt_cd")   String resultCode,
        @JsonProperty("msg_cd")  String messageCode,
        @JsonProperty("msg1")    String message,
        @JsonProperty("output1") List<ExecutionItem> output1
) {
    public boolean isSuccess() { return "0".equals(resultCode); }

    public record ExecutionItem(
            /** 주문일자 (YYYYMMDD) */
            @JsonProperty("ord_dt")     String orderDate,
            /** 주문번호 — order_request.broker_order_no와 매칭 */
            @JsonProperty("odno")     String orderNo,
            /** 원주문번호 */
            @JsonProperty("ord_gno_brno")  String originalOrderNo,
            /** 종목코드 */
            @JsonProperty("pdno")       String stockCode,
            /** 종목명 */
            @JsonProperty("prdt_name")  String stockName,
            /** 매도매수구분 (01=매도, 02=매수) */
            @JsonProperty("sll_buy_dvsn_cd") String orderSideCode,
            /** 매도매수구분명 */
            @JsonProperty("sll_buy_dvsn_cd_name") String orderSideName,
            /** 주문수량 */
            @JsonProperty("ord_qty")    String orderQuantity,
            /** 주문단가 */
            @JsonProperty("ord_unpr")   String orderPrice,
            /** 총체결수량 */
            @JsonProperty("tot_ccld_qty") String executedQuantity,
            /** 총체결금액 */
            @JsonProperty("tot_ccld_amt") String executedAmount,
            /** 미체결수량 */
            @JsonProperty("rmn_qty")   String remainQuantity,
            /** 체결시간 (HHMMSS) */
            @JsonProperty("infm_tmd")  String executedTime
    ) {}
}

