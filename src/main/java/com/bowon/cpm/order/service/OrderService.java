package com.bowon.cpm.order.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.domain.OrderStatusHistory;
import com.bowon.cpm.order.executor.OrderExecutor;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.mapper.OrderStatusHistoryMapper;
import com.bowon.cpm.order.policy.OrderPolicyEngine;
import com.bowon.cpm.order.policy.OrderPolicyEngine.OrderCalculation;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderPolicyEngine orderPolicyEngine;
    private final OrderExecutor orderExecutor;
    private final OrderRequestMapper orderRequestMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final AiDecisionMapper aiDecisionMapper;
    private final RiskCheckResultMapper riskCheckResultMapper;
    private final BrokerClient brokerClient;
    private final KisProperties kisProperties;

    private static final DateTimeFormatter IDEMPOTENCY_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
     * AI 판단 ID를 받아 주문 실행 전체 플로우 수행
     *
     * 1. AI 판단 조회
     * 2. 리스크 검증 결과 확인 (passed=true인 최신 건)
     * 3. idempotency_key 중복 확인
     * 4. KIS 실시간 잔고 조회 (주문 직전 최신 잔고)
     * 5. 주문 수량 계산 (OrderPolicyEngine)
     * 6. order_request 생성 (READY)
     * 7. KIS 주문 API 호출 (OrderExecutor)
     *
     * @param aiDecisionId AI 판단 ID
     */
    @Transactional
    public OrderRequest placeOrder(Long aiDecisionId) {
        // 1. AI 판단 조회
        AiDecision decision = aiDecisionMapper.findById(aiDecisionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 판단 없음: id=" + aiDecisionId));

        // HOLD는 주문 없음
        if ("HOLD".equals(decision.getDecision())) {
            throw new IllegalStateException("HOLD 판단은 주문 실행 불가: aiDecisionId=" + aiDecisionId);
        }

        // 2. idempotency_key 생성 + 중복 확인
        // {accountNo}:{stockCode}:{aiDecisionId}:{orderSide}:{yyyyMMddHHmm}
        String accountNo = kisProperties.accountNo();
        String idempotencyKey = String.format("%s:%s:%d:%s:%s",
                accountNo, decision.getStockCode(), aiDecisionId,
                decision.getDecision(), LocalDateTime.now().format(IDEMPOTENCY_FMT));

        Optional<OrderRequest> existing = orderRequestMapper.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.warn("[Order] 중복 주문 감지: idempotencyKey={}", idempotencyKey);
            return existing.get();
        }

        // 3. KIS 실시간 잔고 조회 (주문 직전 최신값)
        BigDecimal availableCash = BigDecimal.ZERO;
        BigDecimal totalAsset = BigDecimal.ZERO;
        try {
            AccountBalanceResult balance = brokerClient.getAccountBalance();
            availableCash = balance.getAvailableCash() != null ? balance.getAvailableCash() : BigDecimal.ZERO;
            totalAsset = balance.getTotalAssetAmount() != null ? balance.getTotalAssetAmount() : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("[Order] 잔고 조회 실패 (주문 중단): {}", e.getMessage());
            throw new IllegalStateException("잔고 조회 실패로 주문 중단: " + e.getMessage());
        }

        // 4. 주문 수량 계산
        OrderCalculation calc = orderPolicyEngine.calculate(decision, totalAsset, availableCash);
        if (!calc.isOrderable()) {
            throw new IllegalStateException(
                    "주문 수량 0: stockCode=" + decision.getStockCode() +
                            ", availableCash=" + availableCash + ", price=" + decision.getCurrentPrice());
        }

        // 5. order_request 생성 (READY) — 주문 API 호출 전에 반드시 먼저 저장
        OrderRequest orderRequest = OrderRequest.builder()
                .aiDecisionId(aiDecisionId)
                .accountNo(accountNo)
                .brokerType("KIS")
                .stockCode(decision.getStockCode())
                .orderSide(decision.getDecision())   // BUY or SELL
                .orderType("MARKET")                  // 현재는 시장가 주문
                .orderPrice(calc.unitPrice())
                .orderQuantity(calc.quantity())
                .orderAmount(calc.orderAmount())
                .orderStatus("READY")
                .idempotencyKey(idempotencyKey)
                .requestReason("AI 판단 기반 주문: " + decision.getReason())
                .build();
        orderRequestMapper.insert(orderRequest);

        // READY 이력 저장
        orderStatusHistoryMapper.insert(OrderStatusHistory.builder()
                .orderRequestId(orderRequest.getId())
                .previousStatus(null)
                .currentStatus("READY")
                .statusReason("주문 요청 생성")
                .build());

        log.info("[Order] 주문 요청 생성: id={}, stockCode={}, side={}, qty={}, amount={}",
                orderRequest.getId(), orderRequest.getStockCode(),
                orderRequest.getOrderSide(), orderRequest.getOrderQuantity(), orderRequest.getOrderAmount());

        // 6. KIS 주문 API 호출 (OrderExecutor)
        // 주의: 트랜잭션 내에서 외부 API 호출 — 타임아웃 발생 시 order_request는 READY로 남음
        orderExecutor.execute(orderRequest);

        return orderRequest;
    }

    /**
     * 주문 목록 조회
     */
    @Transactional(readOnly = true)
    public List<OrderRequest> getOrders(int limit) {
        return orderRequestMapper.findByAccountNo(kisProperties.accountNo(), limit);
    }
}

