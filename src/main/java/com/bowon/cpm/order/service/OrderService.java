package com.bowon.cpm.order.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.executor.OrderExecutor;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.policy.BuyOrderPolicyEngine;
import com.bowon.cpm.order.policy.SellOrderPolicyEngine;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import com.bowon.cpm.risk.mapper.RiskPolicyConfigMapper;
import com.bowon.cpm.risk.rule.SellRiskManager;
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

    private static final String DEFAULT_POLICY_CODE = "DEFAULT_RISK_POLICY";

    private final BuyOrderPolicyEngine buyOrderPolicyEngine;
    private final SellOrderPolicyEngine sellOrderPolicyEngine;
    private final SellRiskManager sellRiskManager;
    private final OrderExecutor orderExecutor;
    private final OrderRequestCreateService orderRequestCreateService;
    private final OrderRequestMapper orderRequestMapper;
    private final AiDecisionMapper aiDecisionMapper;
    private final RiskCheckResultMapper riskCheckResultMapper;
    private final RiskPolicyConfigMapper riskPolicyConfigMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final BrokerClient brokerClient;
    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;

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
    public OrderRequest placeOrder(Long aiDecisionId) {
        return placeOrderInternal(aiDecisionId, null, true);
    }

    /**
     * 목표가/손절가 트리거 기반 SELL 주문 실행.
     * AI BUY 판단에 연결된 보유 포지션을 청산/부분청산할 때 사용한다.
     */
    public OrderRequest placeSellOrderByTrigger(Long aiDecisionId, SellTrigger trigger) {
        if (trigger == null || SellTrigger.AI_DECISION.equals(trigger)) {
            throw new IllegalArgumentException("트리거 기반 매도에는 TARGET/STOP 트리거가 필요함");
        }
        return placeOrderInternal(aiDecisionId, trigger, false);
    }

    private OrderRequest placeOrderInternal(Long aiDecisionId, SellTrigger forcedSellTrigger, boolean requireRiskPassed) {
        // 1. AI 판단 조회
        AiDecision decision = aiDecisionMapper.findById(aiDecisionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 판단 없음: id=" + aiDecisionId));

        String orderSide = forcedSellTrigger != null ? "SELL" : decision.getDecision();

        // HOLD는 주문 없음
        if ("HOLD".equals(orderSide)) {
            throw new IllegalStateException("HOLD 판단은 주문 실행 불가: aiDecisionId=" + aiDecisionId);
        }

        // HOLD_BY_REVIEW 상태면 주문 차단 (재검토에서 BUY 취소됨)
        if (forcedSellTrigger == null && "HOLD_BY_REVIEW".equals(decision.getDecisionStatus())) {
            throw new IllegalStateException("재검토 결과 HOLD → 주문 차단: aiDecisionId=" + aiDecisionId);
        }

        // 리스크 검증 통과 여부 확인 (passed=true인 최신 건이 있어야 주문 가능)
        if (requireRiskPassed) {
            boolean riskPassed = riskCheckResultMapper.findLatestByAiDecisionId(aiDecisionId)
                    .map(RiskCheckResult::getPassed)
                    .orElse(false);
            if (!riskPassed) {
                throw new IllegalStateException("리스크 검증 미통과 → 주문 차단: aiDecisionId=" + aiDecisionId);
            }
        }

        // 2. idempotency_key 생성 + 중복 확인
        String accountNo = kisProperties.accountNo();
        SellTrigger sellTrigger = "SELL".equals(orderSide)
                ? (forcedSellTrigger != null ? forcedSellTrigger : SellTrigger.AI_DECISION)
                : null;
        String idempotencyKey = buildIdempotencyKey(accountNo, decision, orderSide, sellTrigger);

        Optional<OrderRequest> existing = orderRequestMapper.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.warn("[Order] 중복 주문 감지: idempotencyKey={}", idempotencyKey);
            return existing.get();
        }

        BigDecimal availableCash = BigDecimal.ZERO;
        BigDecimal totalAsset = BigDecimal.ZERO;
        try {
            AccountBalanceResult balance = brokerClient.getAccountBalance();
            availableCash = balance.getAvailableCash() != null ? balance.getAvailableCash() : BigDecimal.ZERO;
            totalAsset = balance.getTotalAssetAmount() != null ? balance.getTotalAssetAmount() : BigDecimal.ZERO;
        } catch (Exception e) {
            if ("BUY".equals(orderSide)) {
                log.warn("[Order] 잔고 조회 실패 (주문 중단): {}", e.getMessage());
                throw new IllegalStateException("잔고 조회 실패로 주문 중단: " + e.getMessage());
            }
            log.warn("[Order] SELL 잔고 조회 실패 (totalAsset=0으로 진행): {}", e.getMessage());
        }

        OrderDraft draft = "BUY".equals(orderSide)
                ? calculateBuy(decision, totalAsset, availableCash)
                : calculateSell(decision, accountNo, totalAsset, sellTrigger);

        if (draft.quantity < 1) {
            throw new IllegalStateException("주문 수량 0: stockCode=" + decision.getStockCode()
                    + ", side=" + orderSide + ", trigger=" + sellTrigger);
        }

        // Save READY before calling the external order API.
        OrderRequest orderRequest = OrderRequest.builder()
                .aiDecisionId(aiDecisionId)
                .accountNo(accountNo)
                .brokerType(tradingProperties.isPaperMode() ? "PAPER" : "KIS")
                .stockCode(decision.getStockCode())
                .orderSide(orderSide)
                .orderType("MARKET")                  // 현재는 시장가 주문
                .orderPrice(draft.unitPrice)
                .orderQuantity(draft.quantity)
                .orderAmount(draft.orderAmount)
                .orderStatus("READY")
                .idempotencyKey(idempotencyKey)
                .requestReason(buildRequestReason(decision, orderSide, sellTrigger))
                .build();
        orderRequestCreateService.createReady(orderRequest);

        log.info("[Order] 주문 요청 생성: id={}, stockCode={}, side={}, qty={}, amount={}",
                orderRequest.getId(), orderRequest.getStockCode(),
                orderRequest.getOrderSide(), orderRequest.getOrderQuantity(), orderRequest.getOrderAmount());

        // 6. KIS 주문 API 호출 (OrderExecutor)
        // 주의: 트랜잭션 내에서 외부 API 호출 — 타임아웃 발생 시 order_request는 READY로 남음
        orderExecutor.execute(orderRequest);

        return orderRequest;
    }

    private OrderDraft calculateBuy(AiDecision decision, BigDecimal totalAsset, BigDecimal availableCash) {
        BuyOrderPolicyEngine.OrderCalculation calc =
                buyOrderPolicyEngine.calculate(decision, totalAsset, availableCash);
        return new OrderDraft(calc.quantity(), calc.orderAmount(), calc.unitPrice());
    }

    private OrderDraft calculateSell(
            AiDecision decision,
            String accountNo,
            BigDecimal totalAsset,
            SellTrigger sellTrigger
    ) {
        PortfolioPosition position = portfolioPositionMapper
                .findByAccountNoAndStockCode(accountNo, decision.getStockCode())
                .orElse(null);

        BigDecimal currentPrice = decision.getCurrentPrice();
        try {
            StockQuoteResult quote = brokerClient.getCurrentPrice(decision.getStockCode());
            if (quote != null && quote.getCurrentPrice() != null) {
                currentPrice = quote.getCurrentPrice();
            }
        } catch (Exception e) {
            log.warn("[Order] SELL 현재가 조회 실패, AI 판단가 사용: {}", e.getMessage());
        }

        boolean hasPreviousPartialSell = orderRequestMapper
                .existsSellByTrigger(decision.getId(), SellTrigger.TARGET_HIT_1.name());
        SellOrderPolicyEngine.SellCalculation calc = sellOrderPolicyEngine.calculate(
                decision, position, currentPrice, totalAsset, sellTrigger, hasPreviousPartialSell);

        var policy = riskPolicyConfigMapper.findByPolicyCode(DEFAULT_POLICY_CODE)
                .orElseThrow(() -> new IllegalStateException("리스크 정책 없음: " + DEFAULT_POLICY_CODE));
        String failReason = sellRiskManager.check(decision, policy, position, calc.quantity(), sellTrigger);
        if (failReason != null) {
            throw new IllegalStateException("SELL 리스크 실패: " + failReason);
        }
        return new OrderDraft(calc.quantity(), calc.orderAmount(), calc.unitPrice());
    }

    private String buildIdempotencyKey(
            String accountNo,
            AiDecision decision,
            String orderSide,
            SellTrigger sellTrigger
    ) {
        String timestamp = LocalDateTime.now().format(IDEMPOTENCY_FMT);
        if ("SELL".equals(orderSide)) {
            return String.format("%s:%s:%d:SELL:%s:%s",
                    accountNo, decision.getStockCode(), decision.getId(), sellTrigger.name(), timestamp);
        }
        return String.format("%s:%s:%d:BUY:%s",
                accountNo, decision.getStockCode(), decision.getId(), timestamp);
    }

    private String buildRequestReason(AiDecision decision, String orderSide, SellTrigger sellTrigger) {
        if ("SELL".equals(orderSide)) {
            return "SELL[" + sellTrigger.name() + "]: " +
                    (decision.getReason() != null ? decision.getReason() : "");
        }
        return "AI 판단 기반 주문: " + decision.getReason();
    }

    private record OrderDraft(int quantity, BigDecimal orderAmount, BigDecimal unitPrice) {}

    /**
     * 주문 목록 조회
     */
    @Transactional(readOnly = true)
    public List<OrderRequest> getOrders(int limit) {
        return orderRequestMapper.findByAccountNo(kisProperties.accountNo(), limit);
    }
}

