package com.bowon.cpm.order.service;

import com.bowon.cpm.broker.kis.KisExecutionClient;
import com.bowon.cpm.broker.kis.dto.KisExecutionResponse;
import com.bowon.cpm.broker.kis.dto.KisExecutionResponse.ExecutionItem;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.feedback.service.RealizedProfitLossService;
import com.bowon.cpm.order.domain.OrderExecution;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.domain.OrderStatusHistory;
import com.bowon.cpm.order.mapper.OrderExecutionMapper;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.mapper.OrderStatusHistoryMapper;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 체결 내역 동기화 서비스
 *
 * KIS 당일 체결 내역을 조회하여:
 * 1. order_execution 저장 (중복 방지)
 * 2. order_request 상태 ORDERED → FILLED 업데이트
 * 3. portfolio_position + account_balance 갱신
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionSyncService {

    private final KisExecutionClient kisExecutionClient;
    private final OrderExecutionMapper orderExecutionMapper;
    private final OrderRequestMapper orderRequestMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final PortfolioService portfolioService;
    private final RealizedProfitLossService realizedProfitLossService;
    private final BrokerApiLogMapper brokerApiLogMapper;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

    /**
     * 당일 체결 내역 동기화
     *
     * @return 새로 저장된 체결 건수
     */
    @Transactional
    public int syncExecutions() {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        int savedCount = 0;

        try {
            LocalDate today = LocalDate.now();
            KisExecutionResponse response = kisExecutionClient.getExecutions(today);

            if (!response.isSuccess()) {
                log.warn("[Execution] 체결 조회 실패: rt_cd={}, msg={}", response.resultCode(), response.message());
                return 0;
            }

            List<ExecutionItem> items = response.output1();
            if (items == null || items.isEmpty()) {
                log.info("[Execution] 당일 체결 없음");
                return 0;
            }

            List<String> newlySavedOrderNos = new ArrayList<>();

            for (ExecutionItem item : items) {
                String brokerOrderNo = item.orderNo();
                if (brokerOrderNo == null || brokerOrderNo.isBlank()) continue;

                // 체결수량이 0이면 스킵 (미체결 주문)
                int execQty = parseIntSafe(item.executedQuantity());
                if (execQty <= 0) continue;

                // 중복 저장 방지 (이미 저장된 체결이면 스킵)
                if (orderExecutionMapper.findByBrokerOrderNo(brokerOrderNo).isPresent()) {
                    log.debug("[Execution] 이미 저장된 체결: brokerOrderNo={}", brokerOrderNo);
                    continue;
                }

                // order_request 조회 (없으면 외부 체결이므로 스킵)
                OrderRequest orderRequest = orderRequestMapper.findByBrokerOrderNo(brokerOrderNo)
                        .orElse(null);
                Long orderRequestId = orderRequest != null ? orderRequest.getId() : null;

                if (orderRequestId == null) {
                    log.warn("[Execution] order_request 매칭 안됨 (외부 체결 스킵): brokerOrderNo={}", brokerOrderNo);
                    continue;
                }

                // 체결 시각 파싱: HHmmss → LocalDateTime
                LocalDateTime executedAt = parseExecutedTime(today, item.executedTime());

                // 체결단가 = 총체결금액 / 총체결수량
                BigDecimal execAmount = parseBigDecimal(item.executedAmount());
                BigDecimal execPrice = execQty > 0 && execAmount.compareTo(BigDecimal.ZERO) > 0
                        ? execAmount.divide(BigDecimal.valueOf(execQty), 0, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                String orderSide = parseSide(item.orderSideName());
                PortfolioPosition positionBeforeExecution = null;
                if (orderRequest != null && "SELL".equals(orderSide)) {
                    positionBeforeExecution = portfolioPositionMapper.findByAccountNoAndStockCode(
                            orderRequest.getAccountNo(), orderRequest.getStockCode()
                    ).orElse(null);
                }

                // order_execution 저장
                OrderExecution execution = OrderExecution.builder()
                        .orderRequestId(orderRequestId)
                        .brokerOrderNo(brokerOrderNo)
                        .stockCode(item.stockCode())
                        .orderSide(orderSide)
                        .executedQuantity(execQty)
                        .executedPrice(execPrice)
                        .executedAmount(execAmount)
                        .executedAt(executedAt)
                        .build();
                orderExecutionMapper.insert(execution);
                realizedProfitLossService.recordSellExecution(
                        execution, orderRequest, positionBeforeExecution);
                savedCount++;

                // order_request 상태: ORDERED → FILLED
                if (orderRequestId != null) {
                    orderRequestMapper.updateStatus(orderRequestId, "FILLED");
                    orderStatusHistoryMapper.insert(OrderStatusHistory.builder()
                            .orderRequestId(orderRequestId)
                            .previousStatus("ORDERED")
                            .currentStatus("FILLED")
                            .statusReason("체결 확인: brokerOrderNo=" + brokerOrderNo
                                    + ", qty=" + item.executedQuantity()
                                    + ", amount=" + item.executedAmount())
                            .build());
                    newlySavedOrderNos.add(brokerOrderNo);
                }

                log.info("[Execution] 체결 저장: brokerOrderNo={}, stockCode={}, side={}, qty={}, amount={}",
                        brokerOrderNo, item.stockCode(), item.orderSideName(),
                        item.executedQuantity(), item.executedAmount());
            }

            // 체결이 1건이라도 있으면 포트폴리오 갱신
            if (savedCount > 0) {
                try {
                    portfolioService.syncAccountBalance();
                    log.info("[Execution] 포트폴리오 갱신 완료");
                } catch (Exception e) {
                    log.warn("[Execution] 포트폴리오 갱신 실패 (체결 저장은 완료): {}", e.getMessage());
                }
            }

            success = true;
            log.info("[Execution] 동기화 완료: 신규 체결 {}건", savedCount);
            return savedCount;

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[Execution] 동기화 실패: {}", errorMessage);
            throw e;
        } finally {
            saveBrokerApiLog(success, errorMessage, System.currentTimeMillis() - start);
        }
    }

    /**
     * broker_order_no로 order_request 조회용 메서드
     * OrderRequestMapper에 추가 필요
     */
    private LocalDateTime parseExecutedTime(LocalDate date, String timeStr) {
        if (timeStr == null || timeStr.length() < 6) return LocalDateTime.of(date, LocalTime.MIDNIGHT);
        try {
            return LocalDateTime.of(date, LocalTime.parse(timeStr.substring(0, 6), TIME_FMT));
        } catch (Exception e) {
            return LocalDateTime.of(date, LocalTime.MIDNIGHT);
        }
    }

    private String parseSide(String sideNameKor) {
        if (sideNameKor == null) return "BUY";
        return sideNameKor.contains("매도") ? "SELL" : "BUY";
    }

    private int parseIntSafe(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Integer.parseInt(value.replace(",", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(value.replace(",", "")); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private void saveBrokerApiLog(boolean success, String errorMessage, long elapsedMs) {
        try {
            brokerApiLogMapper.insert(BrokerApiLog.builder()
                    .brokerType("KIS")
                    .apiName("체결내역조회")
                    .httpMethod("GET")
                    .requestUrl("/uapi/domestic-stock/v1/trading/inquire-daily-ccld")
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

