package com.bowon.cpm.paper.service;

import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.domain.PaperPortfolioPosition;
import com.bowon.cpm.paper.domain.PaperPortfolioProfitLoss;
import com.bowon.cpm.paper.mapper.PaperAccountBalanceMapper;
import com.bowon.cpm.paper.mapper.PaperPortfolioPositionMapper;
import com.bowon.cpm.paper.mapper.PaperPortfolioProfitLossMapper;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaperPortfolioService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String DAILY = "DAILY";
    private static final String PORTFOLIO_STOCK_CODE = "";

    private final PaperAccountBalanceMapper paperAccountBalanceMapper;
    private final PaperPortfolioPositionMapper paperPositionMapper;
    private final PaperPortfolioProfitLossMapper paperProfitLossMapper;
    private final StockService stockService;

    @Transactional
    public void ensureAccountInitialized(String accountNo, BigDecimal seedCash) {
        if (findLatestAccountBalance(accountNo).isPresent()) {
            return;
        }
        BigDecimal cash = zeroIfNull(seedCash);
        paperAccountBalanceMapper.insert(PaperAccountBalance.builder()
                .accountNo(accountNo)
                .baseDatetime(LocalDateTime.now())
                .cashBalance(cash)
                .availableCash(cash)
                .totalAssetAmount(cash)
                .totalEvaluationAmount(ZERO)
                .totalProfitLossAmount(ZERO)
                .totalProfitLossRate(ZERO)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<PaperAccountBalance> findLatestAccountBalance(String accountNo) {
        List<PaperAccountBalance> latest = paperAccountBalanceMapper.findLatestByAccountNo(accountNo);
        return latest.isEmpty() ? Optional.empty() : Optional.of(latest.get(0));
    }

    @Transactional(readOnly = true)
    public PortfolioPosition findPositionAsPortfolio(String accountNo, String stockCode) {
        return paperPositionMapper.findByAccountNoAndStockCode(accountNo, stockCode)
                .map(this::toPortfolioPosition)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PortfolioPosition> findAllHeldAsPortfolio(String accountNo) {
        return paperPositionMapper.findAllHeld(accountNo).stream()
                .map(this::toPortfolioPosition)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaperPortfolioPosition> findPositions(String accountNo) {
        return paperPositionMapper.findByAccountNo(accountNo);
    }

    @Transactional
    public void applyExecution(OrderRequest orderRequest, BigDecimal executedPrice, BigDecimal executedAmount) {
        PaperPortfolioPosition before = paperPositionMapper
                .findByAccountNoAndStockCode(orderRequest.getAccountNo(), orderRequest.getStockCode())
                .orElse(null);
        PaperPortfolioPosition after = calculateAfterPosition(orderRequest, before, executedPrice);
        paperPositionMapper.upsert(after);
        recordAccountSnapshot(orderRequest, zeroIfNull(executedAmount));
    }

    private PaperPortfolioPosition calculateAfterPosition(
            OrderRequest orderRequest,
            PaperPortfolioPosition before,
            BigDecimal executedPrice
    ) {
        BigDecimal price = zeroIfNull(executedPrice);
        int orderQty = orderRequest.getOrderQuantity() != null ? orderRequest.getOrderQuantity() : 0;
        int beforeQty = before != null && before.getQuantity() != null ? before.getQuantity() : 0;
        BigDecimal beforeAvg = before != null && before.getAverageBuyPrice() != null
                ? before.getAverageBuyPrice() : price;

        int afterQty;
        BigDecimal afterAvg;
        if ("BUY".equals(orderRequest.getOrderSide())) {
            afterQty = beforeQty + orderQty;
            BigDecimal beforeAmount = beforeAvg.multiply(BigDecimal.valueOf(beforeQty));
            BigDecimal buyAmount = price.multiply(BigDecimal.valueOf(orderQty));
            afterAvg = afterQty > 0
                    ? beforeAmount.add(buyAmount).divide(BigDecimal.valueOf(afterQty), 4, RoundingMode.HALF_UP)
                    : ZERO;
        } else {
            afterQty = Math.max(0, beforeQty - orderQty);
            afterAvg = beforeAvg;
        }

        BigDecimal valuationAmount = price.multiply(BigDecimal.valueOf(afterQty));
        BigDecimal purchaseAmount = afterAvg.multiply(BigDecimal.valueOf(afterQty));
        BigDecimal profitLossAmount = valuationAmount.subtract(purchaseAmount);
        BigDecimal profitLossRate = calculateRate(profitLossAmount, purchaseAmount);

        return PaperPortfolioPosition.builder()
                .accountNo(orderRequest.getAccountNo())
                .stockCode(orderRequest.getStockCode())
                .stockName(resolveStockName(orderRequest.getStockCode(), before))
                .quantity(afterQty)
                .availableQuantity(afterQty)
                .averageBuyPrice(afterAvg)
                .currentPrice(price)
                .purchaseAmount(purchaseAmount)
                .valuationAmount(valuationAmount)
                .profitLossAmount(profitLossAmount)
                .profitLossRate(profitLossRate)
                .build();
    }

    private void recordAccountSnapshot(OrderRequest orderRequest, BigDecimal executedAmount) {
        PaperAccountBalance previous = findLatestAccountBalance(orderRequest.getAccountNo()).orElse(null);
        BigDecimal beforeCash = previous != null ? zeroIfNull(previous.getCashBalance()) : ZERO;
        BigDecimal cash = "BUY".equals(orderRequest.getOrderSide())
                ? beforeCash.subtract(executedAmount)
                : beforeCash.add(executedAmount);

        List<PaperPortfolioPosition> positions = paperPositionMapper.findAllHeld(orderRequest.getAccountNo());
        BigDecimal totalEvaluation = positions.stream()
                .map(PaperPortfolioPosition::getValuationAmount)
                .map(this::zeroIfNull)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalProfitLoss = positions.stream()
                .map(PaperPortfolioPosition::getProfitLossAmount)
                .map(this::zeroIfNull)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalAsset = cash.add(totalEvaluation);
        BigDecimal totalCostBasis = totalAsset.subtract(totalProfitLoss);

        paperAccountBalanceMapper.insert(PaperAccountBalance.builder()
                .accountNo(orderRequest.getAccountNo())
                .baseDatetime(LocalDateTime.now())
                .cashBalance(cash)
                .availableCash(cash)
                .totalAssetAmount(totalAsset)
                .totalEvaluationAmount(totalEvaluation)
                .totalProfitLossAmount(totalProfitLoss)
                .totalProfitLossRate(calculateRate(totalProfitLoss, totalCostBasis))
                .build());
        upsertDailyProfitLoss(orderRequest.getAccountNo());
    }

    private void upsertDailyProfitLoss(String accountNo) {
        List<PaperAccountBalance> latest = paperAccountBalanceMapper.findLatestByAccountNo(accountNo);
        if (latest.size() < 2) {
            return;
        }

        PaperAccountBalance current = latest.get(0);
        PaperAccountBalance previous = latest.get(1);
        BigDecimal startAsset = zeroIfNull(previous.getTotalAssetAmount());
        BigDecimal endAsset = zeroIfNull(current.getTotalAssetAmount());
        BigDecimal assetDiff = endAsset.subtract(startAsset);

        paperProfitLossMapper.upsert(PaperPortfolioProfitLoss.builder()
                .accountNo(accountNo)
                .stockCode(PORTFOLIO_STOCK_CODE)
                .baseDate(current.getBaseDatetime().toLocalDate())
                .evaluationType(DAILY)
                .startAssetAmount(startAsset)
                .endAssetAmount(endAsset)
                .realizedProfitLoss(ZERO)
                .unrealizedProfitLoss(zeroIfNull(current.getTotalProfitLossAmount()))
                .returnRate(calculateRate(assetDiff, startAsset))
                .build());
    }

    private String resolveStockName(String stockCode, PaperPortfolioPosition before) {
        if (before != null && hasUsableStockName(before.getStockName(), stockCode)) {
            return before.getStockName();
        }
        return stockService.findByStockCode(stockCode)
                .map(StockMaster::getStockName)
                .filter(name -> hasUsableStockName(name, stockCode))
                .orElse(stockCode);
    }

    private boolean hasUsableStockName(String stockName, String stockCode) {
        return stockName != null && !stockName.isBlank() && !stockName.equals(stockCode);
    }

    private PortfolioPosition toPortfolioPosition(PaperPortfolioPosition position) {
        return PortfolioPosition.builder()
                .id(position.getId())
                .accountNo(position.getAccountNo())
                .stockCode(position.getStockCode())
                .stockName(position.getStockName())
                .quantity(position.getQuantity())
                .availableQuantity(position.getAvailableQuantity())
                .averageBuyPrice(position.getAverageBuyPrice())
                .currentPrice(position.getCurrentPrice())
                .purchaseAmount(position.getPurchaseAmount())
                .valuationAmount(position.getValuationAmount())
                .profitLossAmount(position.getProfitLossAmount())
                .profitLossRate(position.getProfitLossRate())
                .updatedAt(position.getUpdatedAt())
                .build();
    }

    private BigDecimal calculateRate(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal base = zeroIfNull(denominator);
        if (base.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return zeroIfNull(numerator)
                .divide(base, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : ZERO;
    }
}
