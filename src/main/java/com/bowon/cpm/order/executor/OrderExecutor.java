package com.bowon.cpm.order.executor;

import com.bowon.cpm.broker.kis.KisOrderClient;
import com.bowon.cpm.broker.kis.dto.KisOrderRequest;
import com.bowon.cpm.broker.kis.dto.KisOrderResponse;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.common.exception.ExternalApiException;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.domain.OrderStatusHistory;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.mapper.OrderStatusHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 주문 실행기
 *
 * KIS API를 호출하여 실제 주문을 전송하고 결과를 DB에 저장한다.
 *
 * 주의:
 * - 타임아웃 발생 시 즉시 재주문하지 않는다.
 * - 주문 전 반드시 order_request가 DB에 READY 상태로 존재해야 한다.
 * - 모든 상태 변경은 order_status_history에 기록한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExecutor {

    private final KisOrderClient kisOrderClient;
    private final OrderRequestMapper orderRequestMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final BrokerApiLogMapper brokerApiLogMapper;

    /**
     * 주문 실행
     * order_request의 orderSide(BUY/SELL)에 따라 매수/매도 API 호출
     *
     * @param orderRequest READY 상태의 주문 요청
     * @return 증권사 주문번호 (broker_order_no)
     */
    public String execute(OrderRequest orderRequest) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String brokerOrderNo = null;
        String errorMessage = null;

        try {
            // 주문 타입: MARKET=시장가("01"), LIMIT=지정가("00")
            String orderDivisionCode = "MARKET".equals(orderRequest.getOrderType()) ? "01" : "00";
            String orderPrice = "MARKET".equals(orderRequest.getOrderType())
                    ? "0"
                    : orderRequest.getOrderPrice().toPlainString();

            KisOrderRequest kisRequest = new KisOrderRequest(
                    orderRequest.getStockCode(),
                    orderRequest.getOrderQuantity(),
                    orderPrice,
                    orderDivisionCode
            );

            // KIS API 호출
            KisOrderResponse response;
            if ("BUY".equals(orderRequest.getOrderSide())) {
                response = kisOrderClient.placeBuyOrder(kisRequest);
            } else {
                response = kisOrderClient.placeSellOrder(kisRequest);
            }

            brokerOrderNo = response.getBrokerOrderNo();
            success = true;

            // order_request: READY → ORDERED + broker_order_no 저장
            orderRequestMapper.updateBrokerOrderNo(orderRequest.getId(), brokerOrderNo, "ORDERED");

            // 상태 이력 저장
            saveStatusHistory(orderRequest.getId(), "READY", "ORDERED",
                    "KIS 주문 접수 완료: brokerOrderNo=" + brokerOrderNo);

            log.info("[Order] 주문 성공: id={}, stockCode={}, side={}, qty={}, brokerOrderNo={}",
                    orderRequest.getId(), orderRequest.getStockCode(),
                    orderRequest.getOrderSide(), orderRequest.getOrderQuantity(), brokerOrderNo);

            return brokerOrderNo;

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[Order] 주문 실패: id={}, stockCode={}, error={}",
                    orderRequest.getId(), orderRequest.getStockCode(), errorMessage);

            // order_request: READY → FAILED
            orderRequestMapper.updateStatus(orderRequest.getId(), "FAILED");
            saveStatusHistory(orderRequest.getId(), "READY", "FAILED", "주문 실패: " + errorMessage);

            throw new ExternalApiException("KIS", "주문 실행 실패: " + errorMessage);

        } finally {
            saveBrokerApiLog(orderRequest, brokerOrderNo, success, errorMessage,
                    System.currentTimeMillis() - start);
        }
    }

    private void saveStatusHistory(Long orderRequestId, String prev, String current, String reason) {
        try {
            orderStatusHistoryMapper.insert(OrderStatusHistory.builder()
                    .orderRequestId(orderRequestId)
                    .previousStatus(prev)
                    .currentStatus(current)
                    .statusReason(reason)
                    .build());
        } catch (Exception e) {
            log.warn("[OrderStatusHistory] 저장 실패: {}", e.getMessage());
        }
    }

    private void saveBrokerApiLog(OrderRequest req, String brokerOrderNo,
                                   boolean success, String errorMessage, long elapsedMs) {
        try {
            brokerApiLogMapper.insert(BrokerApiLog.builder()
                    .brokerType("KIS")
                    .apiName("주문실행(" + req.getOrderSide() + ")")
                    .httpMethod("POST")
                    .requestUrl("/uapi/domestic-stock/v1/trading/order-cash")
                    .responseBody(brokerOrderNo)
                    .success(success)
                    .errorMessage(errorMessage)
                    .elapsedMs(elapsedMs)
                    .calledAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[BrokerApiLog] 저장 실패: {}", e.getMessage());
        }
    }
}

