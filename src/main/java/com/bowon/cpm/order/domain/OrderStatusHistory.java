package com.bowon.cpm.order.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 주문 상태 변경 이력
 * 테이블: order_status_history
 * 주문 관련 모든 상태 변경은 반드시 여기에 기록
 */
@Getter
@Builder
public class OrderStatusHistory {
    @Setter
    private Long id;
    private Long orderRequestId;
    private String previousStatus;
    private String currentStatus;
    private String statusReason;
    private LocalDateTime createdAt;
}
