package com.bowon.cpm.broker;

import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.dto.PortfolioPositionResult;
import com.bowon.cpm.broker.dto.StockQuoteResult;

import java.util.List;

public interface BrokerClient {

    /**
     * 계좌 잔고 조회
     */
    AccountBalanceResult getAccountBalance();

    /**
     * 보유 종목 목록 조회
     */
    List<PortfolioPositionResult> getPositions();

    /**
     * 종목 현재가 조회
     */
    StockQuoteResult getCurrentPrice(String stockCode);
}

