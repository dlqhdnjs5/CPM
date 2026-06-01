package com.bowon.cpm.order.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 요청
 * 테이블: order_request
 * UK: idempotency_key — 동일 조건 중복 주문 방지
 */
@Getter
@Builder
public class OrderRequest {
    /** MyBatis useGeneratedKeys 주입을 위해 setter 필요 */
    @Setter
    private Long id;
    private Long aiDecisionId;
    private String accountNo;
    private String brokerType;
    private String stockCode;
    /** BUY / SELL */
    private String orderSide;
    /** MARKET / LIMIT */
    private String orderType;
    /** 지정가 주문 시 가격 (시장가는 null 또는 0) */
    private BigDecimal orderPrice;
    private Integer orderQuantity;
    private BigDecimal orderAmount;
    /** READY → ORDERED → FILLED / FAILED / CANCELLED */
    private String orderStatus;
    /** {accountNo}:{stockCode}:{aiDecisionId}:{orderSide}:{yyyyMMddHHmm} */
    private String idempotencyKey;
    private String requestReason;
    /** 증권사 주문번호 (KIS ODNO) */
    private String brokerOrderNo;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;
}

