package com.bowon.cpm.order.executor;

import com.bowon.cpm.feedback.service.RealizedProfitLossService;
import com.bowon.cpm.order.domain.OrderExecution;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.mapper.OrderExecutionMapper;
import com.bowon.cpm.order.service.OrderStateService;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaperOrderExecutor {

    private static final DateTimeFormatter BROKER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderStateService orderStateService;
    private final OrderExecutionMapper orderExecutionMapper;
    private final PaperPortfolioService paperPortfolioService;
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

        PortfolioPosition beforePosition = paperPortfolioService
                .findPositionAsPortfolio(orderRequest.getAccountNo(), orderRequest.getStockCode());

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
        paperPortfolioService.applyExecution(orderRequest, executedPrice, executedAmount);

        orderStateService.markFilled(orderRequest.getId(), "ORDERED",
                "PAPER filled: brokerOrderNo=" + brokerOrderNo
                        + ", qty=" + orderRequest.getOrderQuantity()
                        + ", amount=" + executedAmount);

        log.info("[PaperOrder] filled: id={}, side={}, stockCode={}, qty={}, price={}",
                orderRequest.getId(), orderRequest.getOrderSide(),
                orderRequest.getStockCode(), orderRequest.getOrderQuantity(), executedPrice);
        return brokerOrderNo;
    }
}
