package com.bowon.cpm.broker.kis.dto;

/**
 * KIS 주문 요청 DTO
 *
 * @param stockCode         종목 코드 (예: "005930")
 * @param quantity          주문 수량
 * @param orderPrice        주문 단가 (시장가는 "0")
 * @param orderDivisionCode 주문 구분 ("00"=지정가, "01"=시장가)
 */
public record KisOrderRequest(
        String stockCode,
        int quantity,
        String orderPrice,
        String orderDivisionCode
) {
}

