package com.bowon.cpm.feedback.service;

import com.bowon.cpm.feedback.domain.PortfolioRealizedProfitLoss;
import com.bowon.cpm.feedback.mapper.PortfolioRealizedProfitLossMapper;
import com.bowon.cpm.order.domain.OrderExecution;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealizedProfitLossService {

    private final PortfolioRealizedProfitLossMapper realizedProfitLossMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSellExecution(
            OrderExecution execution,
            OrderRequest orderRequest,
            PortfolioPosition positionBeforeExecution
    ) {
        if (execution == null || orderRequest == null || !"SELL".equals(execution.getOrderSide())) {
            return;
        }
        if (execution.getId() == null || execution.getExecutedQuantity() == null
                || execution.getExecutedQuantity() <= 0) {
            return;
        }

        BigDecimal averageBuyPrice = positionBeforeExecution != null
                ? positionBeforeExecution.getAverageBuyPrice() : null;
        BigDecimal executedPrice = execution.getExecutedPrice();
        BigDecimal sellAmount = execution.getExecutedAmount();
        if (executedPrice == null || sellAmount == null) {
            return;
        }

        BigDecimal buyAmount = null;
        BigDecimal realized = null;
        BigDecimal returnRate = null;
        if (averageBuyPrice != null && averageBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
            buyAmount = averageBuyPrice.multiply(BigDecimal.valueOf(execution.getExecutedQuantity()));
            realized = sellAmount.subtract(buyAmount);
            if (buyAmount.compareTo(BigDecimal.ZERO) > 0) {
                returnRate = realized.divide(buyAmount, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(4, RoundingMode.HALF_UP);
            }
        }

        PortfolioRealizedProfitLoss profitLoss = PortfolioRealizedProfitLoss.builder()
                .orderExecutionId(execution.getId())
                .orderRequestId(orderRequest.getId())
                .brokerOrderNo(execution.getBrokerOrderNo())
                .accountNo(orderRequest.getAccountNo())
                .stockCode(execution.getStockCode())
                .executedQuantity(execution.getExecutedQuantity())
                .averageBuyPrice(averageBuyPrice)
                .executedPrice(executedPrice)
                .buyAmount(buyAmount)
                .sellAmount(sellAmount)
                .realizedProfitLoss(realized)
                .returnRate(returnRate)
                .realizedAt(execution.getExecutedAt())
                .build();
        realizedProfitLossMapper.insertIgnore(profitLoss);
        log.info("[RealizedPL] SELL 실현손익 저장: executionId={}, stockCode={}, realized={}, returnRate={}",
                execution.getId(), execution.getStockCode(), realized, returnRate);
    }
}
