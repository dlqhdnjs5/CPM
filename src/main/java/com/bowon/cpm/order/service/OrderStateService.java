package com.bowon.cpm.order.service;

import com.bowon.cpm.order.domain.OrderStatusHistory;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.mapper.OrderStatusHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderStateService {

    private final OrderRequestMapper orderRequestMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOrdered(Long orderRequestId, String brokerOrderNo) {
        orderRequestMapper.updateBrokerOrderNo(orderRequestId, brokerOrderNo, "ORDERED");
        saveHistory(orderRequestId, "READY", "ORDERED",
                "주문 접수 완료: brokerOrderNo=" + brokerOrderNo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFilled(Long orderRequestId, String previousStatus, String reason) {
        orderRequestMapper.updateStatus(orderRequestId, "FILLED");
        saveHistory(orderRequestId, previousStatus, "FILLED", reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long orderRequestId, String previousStatus, String reason) {
        orderRequestMapper.updateStatus(orderRequestId, "FAILED");
        saveHistory(orderRequestId, previousStatus, "FAILED", reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveHistory(Long orderRequestId, String previousStatus, String currentStatus, String reason) {
        orderStatusHistoryMapper.insert(OrderStatusHistory.builder()
                .orderRequestId(orderRequestId)
                .previousStatus(previousStatus)
                .currentStatus(currentStatus)
                .statusReason(reason)
                .build());
    }
}
