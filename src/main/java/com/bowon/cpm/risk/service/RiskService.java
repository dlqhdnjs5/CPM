package com.bowon.cpm.risk.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.LiquidityProperties;
import com.bowon.cpm.common.config.RiskGuardProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.feedback.mapper.PortfolioRealizedProfitLossMapper;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.MarketContext;
import com.bowon.cpm.market.mapper.StockIndicatorDailyMapper;
import com.bowon.cpm.market.service.MarketContextService;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
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
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private final RiskGuardProperties riskGuardProperties;
    private final OrderRequestMapper orderRequestMapper;
    private final StockMasterMapper stockMasterMapper;
    private final MarketContextService marketContextService;
    private final PortfolioRealizedProfitLossMapper realizedProfitLossMapper;
    private final StockIndicatorDailyMapper stockIndicatorDailyMapper;

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
            failReason = guardFailReason(accountNo, balance, decision);
            if (failReason == null) {
                failReason = marketRegimeFailReason(decision);
            }
            if (failReason == null) {
                failReason = technicalOverheatFailReason(decision);
            }
            if (failReason == null) {
                failReason = liquidityFailReason(decision);
            }
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
                paperBalance.getTotalAssetAmount() != null ? paperBalance.getTotalAssetAmount() : BigDecimal.ZERO,
                paperBalance.getTotalProfitLossRate()
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
            return new BalanceContext(availableCash, totalAsset, balanceResult.getTotalProfitLossRate());
        } catch (Exception e) {
            log.warn("[Risk] KIS balance lookup failed, risk check will block with zero cash: {}", e.getMessage());
            return new BalanceContext(BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
    }

    private record BalanceContext(
            BigDecimal availableCash,
            BigDecimal totalAsset,
            BigDecimal totalProfitLossRate
    ) {
    }

    private String guardFailReason(String accountNo, BalanceContext balance, AiDecision decision) {
        if (!"BUY".equals(decision.getDecision())) {
            return null;
        }

        BigDecimal lossLimitRate = riskGuardProperties.dailyLossLimitRate();
        BigDecimal profitLossRate = balance.totalProfitLossRate();
        if (lossLimitRate != null && lossLimitRate.compareTo(BigDecimal.ZERO) > 0 && profitLossRate != null) {
            BigDecimal lossLimitPercent = lossLimitRate.multiply(BigDecimal.valueOf(100)).negate();
            if (profitLossRate.compareTo(lossLimitPercent) <= 0) {
                return String.format("계좌 손실 한도 초과: profitLossRate=%.4f%% <= limit=%.4f%%",
                        profitLossRate, lossLimitPercent);
            }
        }

        BigDecimal reserveRate = riskGuardProperties.minCashReserveRate();
        if (reserveRate != null && reserveRate.compareTo(BigDecimal.ZERO) > 0
                && balance.totalAsset().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal reserveAmount = balance.totalAsset().multiply(reserveRate);
            if (balance.availableCash().compareTo(reserveAmount) <= 0) {
                return String.format("최소 현금 보유율 미달 BUY 차단: availableCash=%.0f <= reserve=%.0f",
                        balance.availableCash(), reserveAmount);
            }
        }

        String diversificationFailReason = diversificationFailReason(accountNo, balance, decision);
        if (diversificationFailReason != null) {
            return diversificationFailReason;
        }

        String brokerType = tradingProperties.isPaperMode() ? "PAPER" : "KIS";
        int stopLossCount = orderRequestMapper.countTodayStopLossSells(accountNo, brokerType);
        int maxStopLossCount = riskGuardProperties.maxDailyStopLossCount();
        if (stopLossCount >= maxStopLossCount) {
            return String.format("일일 손절 횟수 초과: stopLossCount=%d >= max=%d",
                    stopLossCount, maxStopLossCount);
        }

        int cooldownHours = riskGuardProperties.sameStockStopLossCooldownHours();
        LocalDateTime since = LocalDateTime.now().minusHours(cooldownHours);
        if (orderRequestMapper.existsRecentStopLossSellByStock(
                accountNo,
                brokerType,
                decision.getStockCode(),
                since
        )) {
            return String.format("동일 종목 손절 후 재진입 쿨다운: stockCode=%s, cooldownHours=%d",
                    decision.getStockCode(), cooldownHours);
        }

        int realizedLossCooldownHours = riskGuardProperties.realizedLossCooldownHours();
        LocalDateTime realizedLossSince = LocalDateTime.now().minusHours(realizedLossCooldownHours);
        int stockLossCount = realizedProfitLossMapper.countRecentLossesByStock(
                accountNo,
                decision.getStockCode(),
                tradingProperties.isPaperMode(),
                realizedLossSince
        );
        if (stockLossCount > 0) {
            return String.format("동일 종목 최근 실현손실 후 재진입 차단: stockCode=%s, lossCount=%d, cooldownHours=%d",
                    decision.getStockCode(), stockLossCount, realizedLossCooldownHours);
        }

        int accountLossCount = realizedProfitLossMapper.countRecentLossesByAccount(
                accountNo,
                tradingProperties.isPaperMode(),
                realizedLossSince
        );
        int maxRecentLossCount = riskGuardProperties.maxRecentRealizedLossCount();
        if (accountLossCount >= maxRecentLossCount) {
            return String.format("최근 실현손실 연속 구간 BUY 차단: lossCount=%d >= max=%d, lookbackHours=%d",
                    accountLossCount, maxRecentLossCount, realizedLossCooldownHours);
        }

        return null;
    }

    private String marketRegimeFailReason(AiDecision decision) {
        if (!"BUY".equals(decision.getDecision())) {
            return null;
        }

        StockMaster stock = stockMasterMapper.findByStockCode(decision.getStockCode()).orElse(null);
        String marketType = stock != null ? stock.getMarketType() : null;
        String sectorName = stock != null ? stock.getSectorName() : null;
        MarketContext context = marketContextService.latestContext(marketType, sectorName);
        if (context == null) {
            return null;
        }

        BigDecimal marketChangeRate = selectMarketChangeRate(context);
        BigDecimal marketLossBlockRate = riskGuardProperties.marketLossBlockRate();
        if (marketChangeRate != null && marketLossBlockRate != null
                && marketChangeRate.compareTo(marketLossBlockRate) <= 0) {
            return String.format("시장 약세 구간 BUY 차단: marketType=%s, changeRate=%.4f%% <= limit=%.4f%%",
                    context.getMarketType(), marketChangeRate, marketLossBlockRate);
        }

        BigDecimal sectorChangeRate = context.getSectorChangeRate();
        BigDecimal sectorLossBlockRate = riskGuardProperties.sectorLossBlockRate();
        if (sectorChangeRate != null && sectorLossBlockRate != null
                && sectorChangeRate.compareTo(sectorLossBlockRate) <= 0) {
            return String.format("섹터 약세 구간 BUY 차단: sector=%s, changeRate=%.4f%% <= limit=%.4f%%",
                    context.getSectorName(), sectorChangeRate, sectorLossBlockRate);
        }

        return null;
    }

    private String diversificationFailReason(String accountNo, BalanceContext balance, AiDecision decision) {
        if (!"BUY".equals(decision.getDecision())) {
            return null;
        }
        if (balance.totalAsset().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        List<PortfolioPosition> heldPositions = tradingProperties.isPaperMode()
                ? paperPortfolioService.findAllHeldAsPortfolio(accountNo)
                : portfolioPositionMapper.findAllHeld(accountNo);
        if (heldPositions == null) {
            heldPositions = List.of();
        }
        boolean alreadyHolding = heldPositions.stream()
                .anyMatch(position -> decision.getStockCode().equals(position.getStockCode()));
        int maxHeldCount = riskGuardProperties.maxHeldPositionCount();
        if (!alreadyHolding && heldPositions.size() >= maxHeldCount) {
            return String.format("최대 보유 종목 수 초과 BUY 차단: heldCount=%d >= max=%d",
                    heldPositions.size(), maxHeldCount);
        }

        StockMaster targetStock = stockMasterMapper.findByStockCode(decision.getStockCode()).orElse(null);
        String targetSector = normalizeText(targetStock != null ? targetStock.getSectorName() : null);
        if (targetSector == null) {
            return null;
        }

        BigDecimal sectorAmount = BigDecimal.ZERO;
        for (PortfolioPosition position : heldPositions) {
            StockMaster heldStock = stockMasterMapper.findByStockCode(position.getStockCode()).orElse(null);
            if (targetSector.equals(normalizeText(heldStock != null ? heldStock.getSectorName() : null))) {
                sectorAmount = sectorAmount.add(positionAmount(position));
            }
        }

        BigDecimal expectedBuyAmount = expectedBuyAmount(balance, decision);
        BigDecimal sectorExposure = sectorAmount.add(expectedBuyAmount)
                .divide(balance.totalAsset(), 6, java.math.RoundingMode.HALF_UP);
        BigDecimal maxSectorExposure = riskGuardProperties.maxSectorExposureRate();
        if (maxSectorExposure != null && sectorExposure.compareTo(maxSectorExposure) > 0) {
            return String.format("섹터 집중도 초과 BUY 차단: sector=%s, exposure=%.4f > max=%.4f",
                    targetSector, sectorExposure, maxSectorExposure);
        }

        return null;
    }

    private BigDecimal expectedBuyAmount(BalanceContext balance, AiDecision decision) {
        if (decision.getRecommendedPortfolioWeight() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal requestedAmount = balance.totalAsset().multiply(decision.getRecommendedPortfolioWeight());
        BigDecimal reserveAmount = balance.totalAsset().multiply(riskGuardProperties.minCashReserveRate());
        BigDecimal orderableCash = balance.availableCash().subtract(reserveAmount).max(BigDecimal.ZERO);
        return requestedAmount.min(orderableCash);
    }

    private BigDecimal positionAmount(PortfolioPosition position) {
        if (position == null) {
            return BigDecimal.ZERO;
        }
        if (position.getValuationAmount() != null) {
            return position.getValuationAmount();
        }
        if (position.getCurrentPrice() != null && position.getQuantity() != null) {
            return position.getCurrentPrice().multiply(BigDecimal.valueOf(position.getQuantity()));
        }
        if (position.getPurchaseAmount() != null) {
            return position.getPurchaseAmount();
        }
        return BigDecimal.ZERO;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal selectMarketChangeRate(MarketContext context) {
        String marketType = context.getMarketType();
        if ("KOSDAQ".equals(marketType)) {
            return context.getKosdaqChangeRate();
        }
        if ("KOSPI".equals(marketType)) {
            return context.getKospiChangeRate();
        }
        return minNullable(context.getKospiChangeRate(), context.getKosdaqChangeRate());
    }

    private BigDecimal minNullable(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.min(right);
    }

    private String technicalOverheatFailReason(AiDecision decision) {
        if (!"BUY".equals(decision.getDecision())) {
            return null;
        }

        StockIndicatorDaily indicator = stockIndicatorDailyMapper
                .findLatestByStockCode(decision.getStockCode())
                .orElse(null);
        if (indicator == null) {
            return null;
        }

        BigDecimal rsi14 = indicator.getRsi14();
        BigDecimal maxBuyRsi14 = riskGuardProperties.maxBuyRsi14();
        if (rsi14 != null && maxBuyRsi14 != null && rsi14.compareTo(maxBuyRsi14) >= 0) {
            return String.format("기술적 과열 BUY 차단: rsi14=%.4f >= max=%.4f",
                    rsi14, maxBuyRsi14);
        }

        BigDecimal currentPrice = decision.getCurrentPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal ma20Extension = extensionRate(currentPrice, indicator.getMa20());
        BigDecimal maxMa20Extension = riskGuardProperties.maxMa20ExtensionRate();
        if (ma20Extension != null && maxMa20Extension != null
                && ma20Extension.compareTo(maxMa20Extension) >= 0) {
            return String.format("기술적 과열 BUY 차단: ma20Extension=%.4f >= max=%.4f",
                    ma20Extension, maxMa20Extension);
        }

        BigDecimal bollingerUpperExtension = extensionRate(currentPrice, indicator.getBollingerUpper());
        BigDecimal maxBollingerExtension = riskGuardProperties.maxBollingerUpperExtensionRate();
        if (bollingerUpperExtension != null && maxBollingerExtension != null
                && bollingerUpperExtension.compareTo(maxBollingerExtension) >= 0) {
            return String.format("기술적 과열 BUY 차단: bollingerUpperExtension=%.4f >= max=%.4f",
                    bollingerUpperExtension, maxBollingerExtension);
        }

        return null;
    }

    private BigDecimal extensionRate(BigDecimal currentPrice, BigDecimal basePrice) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return currentPrice.subtract(basePrice).divide(basePrice, 6, java.math.RoundingMode.HALF_UP);
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
