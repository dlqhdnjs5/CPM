package com.bowon.cpm.order.executor;

import com.bowon.cpm.feedback.service.RealizedProfitLossService;
import com.bowon.cpm.order.domain.OrderExecution;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.mapper.OrderExecutionMapper;
import com.bowon.cpm.order.service.OrderStateService;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaperOrderExecutor {

    private static final DateTimeFormatter BROKER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderStateService orderStateService;
    private final OrderExecutionMapper orderExecutionMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final RealizedProfitLossService realizedProfitLossService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String execute(OrderRequest orderRequest) {
        String brokerOrderNo = "PAPER-" + orderRequest.getId() + "-" + LocalDateTime.now().format(BROKER_NO_FMT);
        orderStateService.markOrdered(orderRequest.getId(), brokerOrderNo);

        BigDecimal executedPrice = orderRequest.getOrderPrice() != null
                ? orderRequest.getOrderPrice() : BigDecimal.ZERO;
        BigDecimal executedAmount = orderRequest.getOrderAmount() != null
                ? orderRequest.getOrderAmount()
                : executedPrice.multiply(BigDecimal.valueOf(orderRequest.getOrderQuantity()));

        PortfolioPosition beforePosition = portfolioPositionMapper
                .findByAccountNoAndStockCode(orderRequest.getAccountNo(), orderRequest.getStockCode())
                .orElse(null);

        OrderExecution execution = OrderExecution.builder()
                .orderRequestId(orderRequest.getId())
                .brokerOrderNo(brokerOrderNo)
                .stockCode(orderRequest.getStockCode())
                .orderSide(orderRequest.getOrderSide())
                .executedQuantity(orderRequest.getOrderQuantity())
                .executedPrice(executedPrice)
                .executedAmount(executedAmount)
                .executedAt(LocalDateTime.now())
                .build();
        orderExecutionMapper.insert(execution);

        if ("SELL".equals(orderRequest.getOrderSide())) {
            realizedProfitLossService.recordSellExecution(execution, orderRequest, beforePosition);
        }
        updatePaperPosition(orderRequest, beforePosition, executedPrice);

        orderStateService.markFilled(orderRequest.getId(), "ORDERED",
                "PAPER 가상 체결: brokerOrderNo=" + brokerOrderNo
                        + ", qty=" + orderRequest.getOrderQuantity()
                        + ", amount=" + executedAmount);

        log.info("[PaperOrder] 가상 체결 완료: id={}, side={}, stockCode={}, qty={}, price={}",
                orderRequest.getId(), orderRequest.getOrderSide(),
                orderRequest.getStockCode(), orderRequest.getOrderQuantity(), executedPrice);
        return brokerOrderNo;
    }

    private void updatePaperPosition(
            OrderRequest orderRequest,
            PortfolioPosition before,
            BigDecimal executedPrice
    ) {
        int orderQty = orderRequest.getOrderQuantity() != null ? orderRequest.getOrderQuantity() : 0;
        int beforeQty = before != null && before.getQuantity() != null ? before.getQuantity() : 0;
        BigDecimal beforeAvg = before != null && before.getAverageBuyPrice() != null
                ? before.getAverageBuyPrice() : executedPrice;

        int afterQty;
        BigDecimal afterAvg;
        if ("BUY".equals(orderRequest.getOrderSide())) {
            afterQty = beforeQty + orderQty;
            BigDecimal beforeAmount = beforeAvg.multiply(BigDecimal.valueOf(beforeQty));
            BigDecimal buyAmount = executedPrice.multiply(BigDecimal.valueOf(orderQty));
            afterAvg = afterQty > 0
                    ? beforeAmount.add(buyAmount).divide(BigDecimal.valueOf(afterQty), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        } else {
            afterQty = Math.max(0, beforeQty - orderQty);
            afterAvg = beforeAvg;
        }

        BigDecimal valuationAmount = executedPrice.multiply(BigDecimal.valueOf(afterQty));
        BigDecimal purchaseAmount = afterAvg.multiply(BigDecimal.valueOf(afterQty));
        BigDecimal profitLossAmount = valuationAmount.subtract(purchaseAmount);
        BigDecimal profitLossRate = purchaseAmount.compareTo(BigDecimal.ZERO) > 0
                ? profitLossAmount.divide(purchaseAmount, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        portfolioPositionMapper.upsert(PortfolioPosition.builder()
                .accountNo(orderRequest.getAccountNo())
                .stockCode(orderRequest.getStockCode())
                .stockName(before != null ? before.getStockName() : orderRequest.getStockCode())
                .quantity(afterQty)
                .availableQuantity(afterQty)
                .averageBuyPrice(afterAvg)
                .currentPrice(executedPrice)
                .purchaseAmount(purchaseAmount)
                .valuationAmount(valuationAmount)
                .profitLossAmount(profitLossAmount)
                .profitLossRate(profitLossRate)
                .build());
    }
}
