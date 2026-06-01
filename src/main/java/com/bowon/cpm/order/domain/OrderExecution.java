package com.bowon.cpm.order.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 체결 이력
 * 테이블: order_execution
 */
@Getter
@Builder
public class OrderExecution {
    @Setter
    private Long id;
    private Long orderRequestId;
    /** 증권사 주문번호 */
    private String brokerOrderNo;
    private String stockCode;
    /** BUY / SELL */
    private String orderSide;
    private Integer executedQuantity;
    private BigDecimal executedPrice;
    private BigDecimal executedAmount;
    /** 수수료 */
    private BigDecimal commission;
    /** 세금 */
    private BigDecimal tax;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
}
