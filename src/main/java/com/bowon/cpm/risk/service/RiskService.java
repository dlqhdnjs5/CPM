package com.bowon.cpm.risk.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.LiquidityProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.order.trigger.SellTrigger;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.portfolio.service.PortfolioService;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.domain.RiskPolicyConfig;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import com.bowon.cpm.risk.mapper.RiskPolicyConfigMapper;
import com.bowon.cpm.risk.rule.RiskManager;
import com.bowon.cpm.risk.rule.SellRiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private static final String DEFAULT_POLICY_CODE = "DEFAULT_RISK_POLICY";

    private final RiskManager riskManager;
    private final SellRiskManager sellRiskManager;
    private final RiskPolicyConfigMapper riskPolicyConfigMapper;
    private final RiskCheckResultMapper riskCheckResultMapper;
    private final AiDecisionMapper aiDecisionMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final PaperPortfolioService paperPortfolioService;
    private final KisProperties kisProperties;
    private final BrokerClient brokerClient;
    private final TradingProperties tradingProperties;
    private final PortfolioService portfolioService;
    private final LiquidityProperties liquidityProperties;
    private final StockPriceDailyMapper stockPriceDailyMapper;

    @Transactional
    public RiskCheckResult checkAndSave(Long aiDecisionId) {
        AiDecision decision = aiDecisionMapper.findById(aiDecisionId)
                .orElseThrow(() -> new IllegalArgumentException("AI decision not found: id=" + aiDecisionId));

        RiskPolicyConfig policy = riskPolicyConfigMapper.findByPolicyCode(DEFAULT_POLICY_CODE)
                .orElseThrow(() -> new IllegalStateException("Risk policy not found: " + DEFAULT_POLICY_CODE));

        String accountNo = kisProperties.accountNo();
        BalanceContext balance = tradingProperties.isPaperMode()
                ? loadPaperBalance(accountNo)
                : syncRealBalance();

        PortfolioPosition position = tradingProperties.isPaperMode()
                ? paperPortfolioService.findPositionAsPortfolio(accountNo, decision.getStockCode())
                : portfolioPositionMapper
                    .findByAccountNoAndStockCode(accountNo, decision.getStockCode())
                    .orElse(null);

        BigDecimal currentPositionAmount = position != null && position.getValuationAmount() != null
                ? position.getValuationAmount() : BigDecimal.ZERO;
        BigDecimal expectedOrderAmount = BigDecimal.ZERO;
        if (decision.getRecommendedPortfolioWeight() != null
                && balance.totalAsset().compareTo(BigDecimal.ZERO) > 0) {
            expectedOrderAmount = balance.totalAsset().multiply(decision.getRecommendedPortfolioWeight());
        }

        String failReason;
        if ("SELL".equals(decision.getDecision())) {
            failReason = sellRiskManager.check(
                    decision,
                    policy,
                    position,
                    1,
                    SellTrigger.AI_DECISION
            );
        } else {
            failReason = liquidityFailReason(decision);
            if (failReason == null) {
                failReason = riskManager.check(
                    decision,
                    policy,
                    balance.availableCash(),
                    balance.totalAsset(),
                    currentPositionAmount
                );
            }
        }
        boolean passed = failReason == null;

        log.info("[Risk] result: aiDecisionId={}, stockCode={}, decision={}, mode={}, passed={}, failReason={}",
                aiDecisionId, decision.getStockCode(), decision.getDecision(),
                tradingProperties.mode(), passed, failReason);

        RiskCheckResult result = RiskCheckResult.builder()
                .aiDecisionId(aiDecisionId)
                .policyCode(DEFAULT_POLICY_CODE)
                .tradingMode(tradingProperties.normalizedMode())
                .accountNo(accountNo)
                .stockCode(decision.getStockCode())
                .passed(passed)
                .failReason(failReason)
                .availableCash(balance.availableCash())
                .expectedOrderAmount(expectedOrderAmount)
                .maxPositionAmount(balance.totalAsset().compareTo(BigDecimal.ZERO) > 0
                        ? balance.totalAsset().multiply(policy.getMaxPositionWeight()) : null)
                .currentPositionAmount(currentPositionAmount)
                .confidence(decision.getConfidence())
                .riskRewardRatio(decision.getRiskRewardRatio())
                .build();
        riskCheckResultMapper.insert(result);

        return result;
    }

    private BalanceContext loadPaperBalance(String accountNo) {
        try {
            AccountBalanceResult balanceResult = brokerClient.getAccountBalance();
            BigDecimal seedCash = balanceResult.getAvailableCash() != null
                    ? balanceResult.getAvailableCash() : BigDecimal.ZERO;
            paperPortfolioService.ensureAccountInitialized(accountNo, seedCash);
        } catch (Exception e) {
            log.warn("[Risk] PAPER seed balance lookup failed: {}", e.getMessage());
            paperPortfolioService.ensureAccountInitialized(accountNo, BigDecimal.ZERO);
        }

        var paperBalance = paperPortfolioService.findLatestAccountBalance(accountNo)
                .orElseThrow(() -> new IllegalStateException("PAPER account balance not initialized"));
        return new BalanceContext(
                paperBalance.getAvailableCash() != null ? paperBalance.getAvailableCash() : BigDecimal.ZERO,
                paperBalance.getTotalAssetAmount() != null ? paperBalance.getTotalAssetAmount() : BigDecimal.ZERO
        );
    }

    private BalanceContext syncRealBalance() {
        try {
            AccountBalanceResult balanceResult = portfolioService.syncAccountBalance();
            BigDecimal availableCash = balanceResult.getAvailableCash() != null
                    ? balanceResult.getAvailableCash() : BigDecimal.ZERO;
            BigDecimal totalAsset = balanceResult.getTotalAssetAmount() != null
                    ? balanceResult.getTotalAssetAmount() : BigDecimal.ZERO;
            log.info("[Risk] REAL balance synced: availableCash={}, totalAsset={}", availableCash, totalAsset);
            return new BalanceContext(availableCash, totalAsset);
        } catch (Exception e) {
            log.warn("[Risk] KIS balance lookup failed, risk check will block with zero cash: {}", e.getMessage());
            return new BalanceContext(BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private record BalanceContext(BigDecimal availableCash, BigDecimal totalAsset) {
    }

    private String liquidityFailReason(AiDecision decision) {
        if (!"BUY".equals(decision.getDecision())) {
            return null;
        }
        Map<String, Object> averages = stockPriceDailyMapper.findRecentLiquidityAverage(decision.getStockCode(), 20);
        BigDecimal averageVolume = toBigDecimal(averages != null ? averages.get("average_volume") : null);
        BigDecimal averageTradingValue = toBigDecimal(averages != null ? averages.get("average_trading_value") : null);

        BigDecimal minVolume = BigDecimal.valueOf(liquidityProperties.minAverageVolume20());
        BigDecimal minTradingValue = BigDecimal.valueOf(liquidityProperties.minAverageTradingValue20());
        if (averageVolume == null || averageTradingValue == null) {
            return "유동성 데이터 부족: 최근 20거래일 평균 거래량/거래대금 없음";
        }
        if (averageVolume.compareTo(minVolume) < 0) {
            return String.format("유동성 부족: 20일 평균 거래량 %.0f < 기준 %.0f",
                    averageVolume, minVolume);
        }
        if (averageTradingValue.compareTo(minTradingValue) < 0) {
            return String.format("유동성 부족: 20일 평균 거래대금 %.0f < 기준 %.0f",
                    averageTradingValue, minTradingValue);
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }
}
