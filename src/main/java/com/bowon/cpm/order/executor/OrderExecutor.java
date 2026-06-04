package com.bowon.cpm.order.executor;

import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.common.exception.ExternalApiException;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.service.OrderStateService;
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

    private final TradingProperties tradingProperties;
    private final KisOrderExecutor kisOrderExecutor;
    private final PaperOrderExecutor paperOrderExecutor;
    private final OrderStateService orderStateService;
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
            if (tradingProperties.isPaperMode()) {
                brokerOrderNo = paperOrderExecutor.execute(orderRequest);
            } else if (tradingProperties.isRealOrderEnabled()) {
                brokerOrderNo = kisOrderExecutor.execute(orderRequest);
                orderStateService.markOrdered(orderRequest.getId(), brokerOrderNo);
            } else {
                throw new IllegalStateException("REAL 주문 차단: cpm.trading.enabled=false");
            }
            success = true;

            log.info("[Order] 주문 실행 성공: mode={}, id={}, stockCode={}, side={}, qty={}, brokerOrderNo={}",
                    tradingProperties.normalizedMode(),
                    orderRequest.getId(), orderRequest.getStockCode(),
                    orderRequest.getOrderSide(), orderRequest.getOrderQuantity(), brokerOrderNo);

            return brokerOrderNo;

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[Order] 주문 실패: id={}, stockCode={}, error={}",
                    orderRequest.getId(), orderRequest.getStockCode(), errorMessage);

            orderStateService.markFailed(orderRequest.getId(), "READY", "주문 실패: " + errorMessage);

            throw new ExternalApiException(
                    tradingProperties.isPaperMode() ? "PAPER" : "KIS",
                    "Order execution failed: " + errorMessage
            );

        } finally {
            saveBrokerApiLog(orderRequest, brokerOrderNo, success, errorMessage,
                    System.currentTimeMillis() - start);
        }
    }

    private void saveBrokerApiLog(OrderRequest req, String brokerOrderNo,
                                   boolean success, String errorMessage, long elapsedMs) {
        try {
            brokerApiLogMapper.insert(BrokerApiLog.builder()
                    .brokerType(tradingProperties.isPaperMode() ? "PAPER" : "KIS")
                    .apiName("주문실행(" + req.getOrderSide() + "/" + tradingProperties.normalizedMode() + ")")
                    .httpMethod("POST")
                    .requestUrl(tradingProperties.isPaperMode()
                            ? "paper://order"
                            : "/uapi/domestic-stock/v1/trading/order-cash")
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

