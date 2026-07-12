package com.bowon.cpm.order.service;

import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.domain.OrderStatusHistory;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.mapper.OrderStatusHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderRequestCreateService {

    private final OrderRequestMapper orderRequestMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderRequest createReady(OrderRequest orderRequest) {
        orderRequestMapper.insert(orderRequest);
        orderStatusHistoryMapper.insert(OrderStatusHistory.builder()
                .orderRequestId(orderRequest.getId())
                .previousStatus(null)
                .currentStatus("READY")
                .statusReason("주문 요청 생성")
                .build());
        return orderRequest;
    }
}
