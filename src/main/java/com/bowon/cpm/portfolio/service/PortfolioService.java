package com.bowon.cpm.portfolio.service;

import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.dto.PortfolioPositionResult;
import com.bowon.cpm.broker.kis.KisAccountClient;
import com.bowon.cpm.broker.kis.KisMapper;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.broker.kis.dto.KisBalanceResponse;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.portfolio.domain.AccountBalance;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.mapper.AccountBalanceMapper;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    // BrokerClient 대신 KisAccountClient를 직접 사용 — 잔고+보유종목을 1회 API 호출로 처리
    private final KisAccountClient kisAccountClient;
    private final KisProperties kisProperties;
    private final AccountBalanceMapper accountBalanceMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final BrokerApiLogMapper brokerApiLogMapper;

    /**
     * KIS 잔고 조회 API를 1회만 호출하여 계좌 잔고와 보유 종목을 모두 DB에 저장한다.
     *
     * REQUIRES_NEW: 호출 측 트랜잭션과 별개로 실행
     * → 이 메서드가 실패해도 호출 측 트랜잭션에 영향 없음
     * → RiskService 등에서 잔고 동기화 실패를 graceful 처리 가능
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountBalanceResult syncAccountBalance() {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        AccountBalanceResult balanceResult;

        try {
            // KIS API 1회 호출 — output1(보유종목) + output2(잔고) 동시 수신
            KisBalanceResponse response = kisAccountClient.getBalance();

            // 잔고 변환 및 저장
            balanceResult = KisMapper.toAccountBalanceResult(kisProperties.accountNo(), response);
            accountBalanceMapper.insert(AccountBalance.builder()
                    .accountNo(balanceResult.getAccountNo())
                    .brokerType("KIS")
                    .baseDatetime(LocalDateTime.now())
                    .cashBalance(balanceResult.getCashBalance())
                    .availableCash(balanceResult.getAvailableCash())
                    .totalAssetAmount(balanceResult.getTotalAssetAmount())
                    .totalEvaluationAmount(balanceResult.getTotalEvaluationAmount())
                    .totalProfitLossAmount(balanceResult.getTotalProfitLossAmount())
                    .totalProfitLossRate(balanceResult.getTotalProfitLossRate())
                    .build());

            // 보유종목 변환 및 저장 (보유종목 없으면 빈 리스트 — 정상)
            List<PortfolioPositionResult> positions = KisMapper.toPortfolioPositions(response);
            for (PortfolioPositionResult pos : positions) {
                portfolioPositionMapper.upsert(PortfolioPosition.builder()
                        .accountNo(kisProperties.accountNo())
                        .stockCode(pos.getStockCode())
                        .stockName(pos.getStockName())
                        .quantity(pos.getQuantity())
                        .availableQuantity(pos.getAvailableQuantity())
                        .averageBuyPrice(pos.getAverageBuyPrice())
                        .currentPrice(pos.getCurrentPrice())
                        .valuationAmount(pos.getValuationAmount())
                        .profitLossAmount(pos.getProfitLossAmount())
                        .profitLossRate(pos.getProfitLossRate())
                        .build());
            }

            success = true;
            log.info("[Portfolio] 계좌 동기화 완료: accountNo={}, cashBalance={}, positions={}",
                    balanceResult.getAccountNo(), balanceResult.getCashBalance(), positions.size());

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[Portfolio] 계좌 동기화 실패: {}", errorMessage);
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            saveBrokerApiLog(success, errorMessage, elapsed);
        }

        return balanceResult;
    }

    /**
     * DB에서 현재 보유 종목 목록 조회 (KIS API 호출 없음)
     */
    @Transactional(readOnly = true)
    public List<PortfolioPosition> getPositions() {
        return portfolioPositionMapper.findByAccountNo(kisProperties.accountNo());
    }

    private void saveBrokerApiLog(boolean success, String errorMessage, long elapsedMs) {
        try {
            brokerApiLogMapper.insert(BrokerApiLog.builder()
                    .brokerType("KIS")
                    .apiName("계좌잔고조회")
                    .httpMethod("GET")
                    .requestUrl("/uapi/domestic-stock/v1/trading/inquire-balance")
                    .success(success)
                    .errorMessage(errorMessage)
                    .elapsedMs(elapsedMs)
                    .calledAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[BrokerApiLog] 로그 저장 실패: {}", e.getMessage());
        }
    }
}
