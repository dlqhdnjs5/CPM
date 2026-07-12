package com.bowon.cpm.scheduler;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.order.service.OrderService;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 목표가/손절가 감시 스케줄러.
 *
 * 장중 보유 종목을 확인해 최신 BUY 판단의 target/stop 가격을 기준으로
 * 자동 SELL 주문을 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetStopMonitorScheduler {

    private static final String NAME = "TargetStopMonitorScheduler";

    private final SchedulerLogSupport logSupport;
    private final KisProperties kisProperties;
    private final TradingProperties tradingProperties;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final PaperPortfolioService paperPortfolioService;
    private final AiDecisionMapper aiDecisionMapper;
    private final OrderRequestMapper orderRequestMapper;
    private final BrokerClient brokerClient;
    private final OrderService orderService;

    @Scheduled(cron = "0 */1 9-15 * * MON-FRI")
    public void run() {
        if (!logSupport.isMarketOpen() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        int checked = 0, ordered = 0, skipped = 0, failed = 0;
        try {
            List<PortfolioPosition> positions = tradingProperties.isPaperMode()
                    ? paperPortfolioService.findAllHeldAsPortfolio(kisProperties.accountNo())
                    : portfolioPositionMapper.findAllHeld(kisProperties.accountNo());
            for (PortfolioPosition position : positions) {
                checked++;
                try {
                    AiDecision decision = aiDecisionMapper
                            .findLatestBuyForActivePosition(position.getStockCode())
                            .orElse(null);
                    if (decision == null) {
                        skipped++;
                        continue;
                    }

                    BigDecimal currentPrice = fetchCurrentPrice(position.getStockCode());
                    if (currentPrice == null) {
                        skipped++;
                        continue;
                    }

                    SellTrigger trigger = decideTrigger(decision, currentPrice);
                    if (trigger == null) {
                        skipped++;
                        continue;
                    }

                    if (orderRequestMapper.existsTodaySellByTrigger(decision.getId(), trigger.name())) {
                        skipped++;
                        continue;
                    }

                    orderService.placeSellOrderByTrigger(decision.getId(), trigger);
                    ordered++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[{}] {} 처리 실패: {}", NAME, position.getStockCode(), e.getMessage());
                }
            }
            logSupport.success(logId, "확인:" + checked + ", 주문:" + ordered
                    + ", 스킵:" + skipped + ", 실패:" + failed);
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }

    private BigDecimal fetchCurrentPrice(String stockCode) {
        try {
            StockQuoteResult quote = brokerClient.getCurrentPrice(stockCode);
            return quote != null ? quote.getCurrentPrice() : null;
        } catch (Exception e) {
            log.warn("[{}] 현재가 조회 실패 stockCode={}: {}", NAME, stockCode, e.getMessage());
            return null;
        }
    }

    private SellTrigger decideTrigger(AiDecision decision, BigDecimal currentPrice) {
        if (decision.getStopLossPrice() != null
                && currentPrice.compareTo(decision.getStopLossPrice()) <= 0) {
            return SellTrigger.STOP_LOSS_HIT;
        }
        if (decision.getTargetPrice() != null
                && currentPrice.compareTo(decision.getTargetPrice()) >= 0) {
            boolean hasFirstTargetSell = orderRequestMapper
                    .existsSellByTrigger(decision.getId(), SellTrigger.TARGET_HIT_1.name());
            return hasFirstTargetSell ? SellTrigger.TARGET_HIT_2 : SellTrigger.TARGET_HIT_1;
        }
        return null;
    }
}
