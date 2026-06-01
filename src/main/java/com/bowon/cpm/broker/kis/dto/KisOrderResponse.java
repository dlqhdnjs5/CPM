package com.bowon.cpm.broker.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KIS 주문 응답 DTO
 *
 * 성공 응답 예시:
 * {
 *   "rt_cd": "0",
 *   "msg_cd": "APBK0013",
 *   "msg1": "주문 전송 완료",
 *   "output": {
 *     "KRX_FWDG_ORD_ORGNO": "91011",
 *     "ODNO": "0000117057",   ← 주문번호 (broker_order_no)
 *     "ORD_TMD": "130000"
 *   }
 * }
 */
public record KisOrderResponse(
        @JsonProperty("rt_cd")
        String resultCode,

        @JsonProperty("msg_cd")
        String messageCode,

        @JsonProperty("msg1")
        String message,

        @JsonProperty("output")
        Output output
) {
    public record Output(
            /** KRX 전송 주문 조직 번호 */
            @JsonProperty("KRX_FWDG_ORD_ORGNO")
            String orderOrgNo,

            /** 주문 번호 — broker_order_no로 저장 */
            @JsonProperty("ODNO")
            String orderNo,

            /** 주문 시각 (HHmmss) */
            @JsonProperty("ORD_TMD")
            String orderTime
    ) {
    }

    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

    public String getBrokerOrderNo() {
        return output != null ? output.orderNo() : null;
    }
}

