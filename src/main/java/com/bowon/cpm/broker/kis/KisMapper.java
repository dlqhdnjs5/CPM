package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.dto.PortfolioPositionResult;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.dto.KisBalanceResponse;
import com.bowon.cpm.broker.kis.dto.KisCurrentPriceResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class KisMapper {

    private KisMapper() {
    }

    public static StockQuoteResult toStockQuoteResult(String stockCode, KisCurrentPriceResponse response) {
        KisCurrentPriceResponse.Output output = response.output();
        return StockQuoteResult.builder()
                .stockCode(stockCode)
                .stockName(output.stockName())
                .currentPrice(parseBigDecimal(output.currentPrice()))
                .changePrice(parseBigDecimal(output.changePrice()))
                .changeRate(parseBigDecimal(output.changeRate()))
                .accumulatedVolume(parseLong(output.accumulatedVolume()))
                .tradingValue(parseBigDecimal(output.accumulatedTradingValue()))
                .quoteTime(LocalDateTime.now())
                .build();
    }

    public static AccountBalanceResult toAccountBalanceResult(String accountNo, KisBalanceResponse response) {
        if (response.balances() == null || response.balances().isEmpty()) {
            return AccountBalanceResult.builder()
                    .accountNo(accountNo)
                    .cashBalance(BigDecimal.ZERO)
                    .availableCash(BigDecimal.ZERO)
                    .totalAssetAmount(BigDecimal.ZERO)
                    .totalEvaluationAmount(BigDecimal.ZERO)
                    .build();
        }
        KisBalanceResponse.BalanceOutput b = response.balances().get(0);
        return AccountBalanceResult.builder()
                .accountNo(accountNo)
                .cashBalance(parseBigDecimal(b.cashBalance()))
                .availableCash(parseBigDecimal(b.availableCash()))
                .totalAssetAmount(parseBigDecimal(b.totalAssetAmount()))
                .totalEvaluationAmount(parseBigDecimal(b.stockEvaluationAmount()))
                .totalProfitLossAmount(parseBigDecimal(b.totalProfitLossAmount()))
                .totalProfitLossRate(parseBigDecimal(b.totalProfitLossRate()))
                .build();
    }

    public static List<PortfolioPositionResult> toPortfolioPositions(KisBalanceResponse response) {
        if (response.positions() == null) {
            return Collections.emptyList();
        }
        return response.positions().stream()
                .filter(p -> p.stockCode() != null && !p.stockCode().isBlank())
                .map(p -> PortfolioPositionResult.builder()
                        .stockCode(p.stockCode())
                        .stockName(p.stockName())
                        .quantity(parseInteger(p.quantity()))
                        .availableQuantity(parseInteger(p.availableQuantity()))
                        .averageBuyPrice(parseBigDecimal(p.averageBuyPrice()))
                        .currentPrice(parseBigDecimal(p.currentPrice()))
                        .valuationAmount(parseBigDecimal(p.valuationAmount()))
                        .profitLossAmount(parseBigDecimal(p.profitLossAmount()))
                        .profitLossRate(parseBigDecimal(p.profitLossRate()))
                        .build())
                .collect(Collectors.toList());
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

