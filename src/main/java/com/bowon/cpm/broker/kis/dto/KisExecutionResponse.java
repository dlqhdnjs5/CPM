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
            /** 주문번호 — order_request.broker_order_no와 매칭 */
            @JsonProperty("ODNO")       String orderNo,
            /** 종목코드 */
            @JsonProperty("PDNO")       String stockCode,
            /** 종목명 */
            @JsonProperty("PRDT_NAME")  String stockName,
            /** 매도매수구분명 (매수/매도) */
            @JsonProperty("SLL_BUY_DVSN_CD_NAME") String orderSideName,
            /** 주문수량 */
            @JsonProperty("ORD_QTY")    String orderQuantity,
            /** 체결수량 */
            @JsonProperty("CCLD_QTY")   String executedQuantity,
            /** 체결단가 */
            @JsonProperty("CCLD_UNPR")  String executedPrice,
            /** 체결금액 */
            @JsonProperty("CCLD_AMT")   String executedAmount,
            /** 주문시각 */
            @JsonProperty("ORD_TMD")    String orderTime,
            /** 체결시각 */
            @JsonProperty("CCLD_TMD")   String executedTime
    ) {}
}

