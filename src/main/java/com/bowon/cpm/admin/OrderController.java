package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * AI 판단 기반 실전 주문 실행
     * POST /api/orders/requests/{aiDecisionId}
     *
     * 흐름: AI 판단 → 리스크 검증 → 주문 수량 계산 → KIS 주문 API 호출
     * 주의: 실제 계좌로 주문이 나갑니다.
     */
    @PostMapping("/requests/{aiDecisionId}")
    public ApiResponse<Map<String, Object>> placeOrder(@PathVariable Long aiDecisionId) {
        OrderRequest order = orderService.placeOrder(aiDecisionId);
        return ApiResponse.ok("주문 완료", Map.of(
                "orderId", order.getId(),
                "stockCode", order.getStockCode(),
                "orderSide", order.getOrderSide(),
                "orderType", order.getOrderType(),
                "quantity", order.getOrderQuantity(),
                "amount", order.getOrderAmount(),
                "orderStatus", order.getOrderStatus(),
                "brokerOrderNo", order.getBrokerOrderNo() != null ? order.getBrokerOrderNo() : ""
        ));
    }

    /**
     * 주문 목록 조회
     * GET /api/orders/requests?limit=20
     */
    @GetMapping("/requests")
    public ApiResponse<List<OrderRequest>> getOrders(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(orderService.getOrders(limit));
    }
}

