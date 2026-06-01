package com.bowon.cpm.broker.kis;

import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.dto.PortfolioPositionResult;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.dto.KisBalanceResponse;
import com.bowon.cpm.broker.kis.dto.KisCurrentPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KisBrokerClient implements BrokerClient {

    private final KisAccountClient accountClient;
    private final KisMarketClient marketClient;
    private final KisProperties properties;

    @Override
    public AccountBalanceResult getAccountBalance() {
        KisBalanceResponse response = accountClient.getBalance();
        return KisMapper.toAccountBalanceResult(properties.accountNo(), response);
    }

    @Override
    public List<PortfolioPositionResult> getPositions() {
        KisBalanceResponse response = accountClient.getBalance();
        return KisMapper.toPortfolioPositions(response);
    }

    @Override
    public StockQuoteResult getCurrentPrice(String stockCode) {
        KisCurrentPriceResponse response = marketClient.getCurrentPrice(stockCode);
        return KisMapper.toStockQuoteResult(stockCode, response);
    }
}

