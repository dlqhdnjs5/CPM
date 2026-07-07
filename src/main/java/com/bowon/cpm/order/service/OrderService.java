package com.bowon.cpm.order.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.RiskGuardProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.executor.OrderExecutor;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.policy.BuyOrderPolicyEngine;
import com.bowon.cpm.order.policy.SellOrderPolicyEngine;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.paper.service.PaperPortfolioService;
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
    private static final DateTimeFormatter IDEMPOTENCY_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

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
    private final PaperPortfolioService paperPortfolioService;
    private final BrokerClient brokerClient;
    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;
    private final RiskGuardProperties riskGuardProperties;

    public OrderRequest placeOrder(Long aiDecisionId) {
        return placeOrderInternal(aiDecisionId, null, true);
    }

    public OrderRequest placeSellOrderByTrigger(Long aiDecisionId, SellTrigger trigger) {
        if (trigger == null || SellTrigger.AI_DECISION.equals(trigger)) {
            throw new IllegalArgumentException("trigger based sell requires TARGET or STOP trigger");
        }
        return placeOrderInternal(aiDecisionId, trigger, false);
    }

    private OrderRequest placeOrderInternal(Long aiDecisionId, SellTrigger forcedSellTrigger, boolean requireRiskPassed) {
        AiDecision decision = aiDecisionMapper.findById(aiDecisionId)
                .orElseThrow(() -> new IllegalArgumentException("AI decision not found: id=" + aiDecisionId));

        String orderSide = forcedSellTrigger != null ? "SELL" : decision.getDecision();
        if ("HOLD".equals(orderSide)) {
            throw new IllegalStateException("HOLD decision cannot create order: aiDecisionId=" + aiDecisionId);
        }

        if (forcedSellTrigger == null && "HOLD_BY_REVIEW".equals(decision.getDecisionStatus())) {
            throw new IllegalStateException("review changed decision to HOLD - order blocked: aiDecisionId=" + aiDecisionId);
        }

        if (requireRiskPassed) {
            RiskCheckResult riskCheck = riskCheckResultMapper.findLatestByAiDecisionIdAndTradingMode(
                            aiDecisionId, tradingProperties.normalizedMode())
                    .orElseThrow(() -> new IllegalStateException(
                            "risk check result missing - order blocked: aiDecisionId=" + aiDecisionId));
            validateFreshPassedRisk(aiDecisionId, riskCheck);
        }

        String accountNo = kisProperties.accountNo();
        SellTrigger sellTrigger = "SELL".equals(orderSide)
                ? (forcedSellTrigger != null ? forcedSellTrigger : SellTrigger.AI_DECISION)
                : null;
        String idempotencyKey = buildIdempotencyKey(accountNo, decision, orderSide, sellTrigger);

        Optional<OrderRequest> existing = orderRequestMapper.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.warn("[Order] duplicate order detected: idempotencyKey={}", idempotencyKey);
            return existing.get();
        }

        BigDecimal availableCash = BigDecimal.ZERO;
        BigDecimal totalAsset = BigDecimal.ZERO;
        try {
            AccountBalanceResult balance = brokerClient.getAccountBalance();
            availableCash = balance.getAvailableCash() != null ? balance.getAvailableCash() : BigDecimal.ZERO;
            totalAsset = balance.getTotalAssetAmount() != null ? balance.getTotalAssetAmount() : BigDecimal.ZERO;
        } catch (Exception e) {
            if ("BUY".equals(orderSide) && !tradingProperties.isPaperMode()) {
                log.warn("[Order] balance lookup failed, BUY blocked: {}", e.getMessage());
                throw new IllegalStateException("balance lookup failed - order blocked: " + e.getMessage());
            }
            log.warn("[Order] balance lookup failed for SELL, continuing with totalAsset=0: {}", e.getMessage());
        }

        if (tradingProperties.isPaperMode()) {
            paperPortfolioService.ensureAccountInitialized(accountNo, availableCash);
            var paperBalance = paperPortfolioService.findLatestAccountBalance(accountNo)
                    .orElseThrow(() -> new IllegalStateException("PAPER account balance not initialized"));
            availableCash = paperBalance.getAvailableCash() != null
                    ? paperBalance.getAvailableCash() : BigDecimal.ZERO;
            totalAsset = paperBalance.getTotalAssetAmount() != null
                    ? paperBalance.getTotalAssetAmount() : BigDecimal.ZERO;
        }

        OrderDraft draft = "BUY".equals(orderSide)
                ? calculateBuy(decision, totalAsset, availableCash)
                : calculateSell(decision, accountNo, totalAsset, sellTrigger);

        if (draft.quantity < 1) {
            throw new IllegalStateException("order quantity is zero: stockCode=" + decision.getStockCode()
                    + ", side=" + orderSide + ", trigger=" + sellTrigger);
        }

        OrderRequest orderRequest = OrderRequest.builder()
                .aiDecisionId(aiDecisionId)
                .accountNo(accountNo)
                .brokerType(tradingProperties.isPaperMode() ? "PAPER" : "KIS")
                .stockCode(decision.getStockCode())
                .orderSide(orderSide)
                .orderType("MARKET")
                .orderPrice(draft.unitPrice)
                .orderQuantity(draft.quantity)
                .orderAmount(draft.orderAmount)
                .orderStatus("READY")
                .idempotencyKey(idempotencyKey)
                .requestReason(buildRequestReason(decision, orderSide, sellTrigger))
                .build();
        orderRequestCreateService.createReady(orderRequest);

        log.info("[Order] request created: id={}, stockCode={}, side={}, qty={}, amount={}",
                orderRequest.getId(), orderRequest.getStockCode(),
                orderRequest.getOrderSide(), orderRequest.getOrderQuantity(), orderRequest.getOrderAmount());

        orderExecutor.execute(orderRequest);

        return orderRequest;
    }

    private void validateFreshPassedRisk(Long aiDecisionId, RiskCheckResult riskCheck) {
        if (!Boolean.TRUE.equals(riskCheck.getPassed())) {
            throw new IllegalStateException("risk check failed - order blocked: aiDecisionId=" + aiDecisionId
                    + ", reason=" + riskCheck.getFailReason());
        }
        if (riskCheck.getCheckedAt() == null) {
            throw new IllegalStateException("risk check timestamp missing - order blocked: aiDecisionId=" + aiDecisionId);
        }

        LocalDateTime expiresAt = riskCheck.getCheckedAt()
                .plusMinutes(riskGuardProperties.maxRiskCheckAgeMinutes());
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new IllegalStateException("risk check expired - order blocked: aiDecisionId=" + aiDecisionId
                    + ", checkedAt=" + riskCheck.getCheckedAt()
                    + ", maxAgeMinutes=" + riskGuardProperties.maxRiskCheckAgeMinutes());
        }
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
        PortfolioPosition position = tradingProperties.isPaperMode()
                ? paperPortfolioService.findPositionAsPortfolio(accountNo, decision.getStockCode())
                : portfolioPositionMapper
                    .findByAccountNoAndStockCode(accountNo, decision.getStockCode())
                    .orElse(null);

        BigDecimal currentPrice = decision.getCurrentPrice();
        try {
            StockQuoteResult quote = brokerClient.getCurrentPrice(decision.getStockCode());
            if (quote != null && quote.getCurrentPrice() != null) {
                currentPrice = quote.getCurrentPrice();
            }
        } catch (Exception e) {
            log.warn("[Order] SELL current price lookup failed, using AI decision price: {}", e.getMessage());
        }

        boolean hasPreviousPartialSell = orderRequestMapper
                .existsSellByTrigger(decision.getId(), SellTrigger.TARGET_HIT_1.name());
        SellOrderPolicyEngine.SellCalculation calc = sellOrderPolicyEngine.calculate(
                decision, position, currentPrice, totalAsset, sellTrigger, hasPreviousPartialSell);

        var policy = riskPolicyConfigMapper.findByPolicyCode(DEFAULT_POLICY_CODE)
                .orElseThrow(() -> new IllegalStateException("risk policy not found: " + DEFAULT_POLICY_CODE));
        String failReason = sellRiskManager.check(decision, policy, position, calc.quantity(), sellTrigger);
        if (failReason != null) {
            throw new IllegalStateException("SELL risk check failed: " + failReason);
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
        return "AI decision based order: " + decision.getReason();
    }

    private record OrderDraft(int quantity, BigDecimal orderAmount, BigDecimal unitPrice) {}

    @Transactional(readOnly = true)
    public List<OrderRequest> getOrders(String mode, int limit) {
        return orderRequestMapper.findByAccountNo(
                kisProperties.accountNo(),
                normalizeMode(mode),
                limit
        );
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null || mode.isBlank()
                ? tradingProperties.normalizedMode()
                : mode.trim().toUpperCase();
        if ("REAL".equals(normalized)) {
            return "KIS";
        }
        return normalized;
    }
}
