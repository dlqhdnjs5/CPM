package com.bowon.cpm.dashboard.service;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.domain.ExternalApiCallLog;
import com.bowon.cpm.common.domain.SchedulerExecutionLog;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.common.mapper.ExternalApiCallLogMapper;
import com.bowon.cpm.common.mapper.SchedulerExecutionLogMapper;
import com.bowon.cpm.dashboard.domain.DashboardAiTradeHistoryItem;
import com.bowon.cpm.dashboard.domain.DashboardSummary;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.order.mapper.OrderRequestMapper;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.domain.PaperPortfolioPosition;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.risk.mapper.RiskCheckResultMapper;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_LIMIT = 12;
    private static final int TRADE_HISTORY_DEFAULT_LIMIT = 50;
    private static final int TRADE_HISTORY_MAX_LIMIT = 100;

    private final TradingProperties tradingProperties;
    private final KisProperties kisProperties;
    private final PaperPortfolioService paperPortfolioService;
    private final StockMasterMapper stockMasterMapper;
    private final AiDecisionMapper aiDecisionMapper;
    private final RiskCheckResultMapper riskCheckResultMapper;
    private final OrderRequestMapper orderRequestMapper;
    private final SchedulerExecutionLogMapper schedulerExecutionLogMapper;
    private final ExternalApiCallLogMapper externalApiCallLogMapper;
    private final BrokerApiLogMapper brokerApiLogMapper;

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        String accountNo = kisProperties.accountNo();
        List<StockMaster> activeStocks = stockMasterMapper.findAllActive();
        List<StockMaster> stockMasterList = stockMasterMapper.findAll();
        List<AiDecision> recentDecisions = aiDecisionMapper.findRecent(RECENT_LIMIT);
        List<RiskCheckResult> recentRiskChecks = riskCheckResultMapper.findRecent(RECENT_LIMIT);

        DashboardSummary.DecisionSummary decisionSummary = new DashboardSummary.DecisionSummary(
                countDecision(recentDecisions, "BUY"),
                countDecision(recentDecisions, "HOLD"),
                countDecision(recentDecisions, "SELL"),
                recentRiskChecks.stream().filter(item -> Boolean.FALSE.equals(item.getPassed())).count()
        );

        PaperAccountBalance paperBalance = paperPortfolioService.findLatestAccountBalance(accountNo).orElse(null);
        List<PaperPortfolioPosition> paperPositions = paperPortfolioService.findPositions(accountNo);

        return new DashboardSummary(
                LocalDateTime.now(),
                new DashboardSummary.SystemStatus(
                        tradingProperties.normalizedMode(),
                        tradingProperties.enabled(),
                        maskAccountNo(accountNo),
                        activeStocks.size()
                ),
                activeStocks,
                stockMasterList,
                paperBalance,
                paperPositions,
                decisionSummary,
                recentDecisions,
                recentRiskChecks,
                orderRequestMapper.findByAccountNo(accountNo, "ALL", RECENT_LIMIT),
                schedulerExecutionLogMapper.findRecent(RECENT_LIMIT),
                externalApiCallLogMapper.findRecentFailures(RECENT_LIMIT),
                brokerApiLogMapper.findRecentFailures(RECENT_LIMIT)
        );
    }

    private long countDecision(List<AiDecision> decisions, String decision) {
        return decisions.stream()
                .filter(item -> decision.equals(item.getDecision()))
                .count();
    }

    private String maskAccountNo(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "N/A";
        }
        String compact = accountNo.trim();
        if (compact.length() <= 4) {
            return "****";
        }
        return compact.substring(0, 4) + "****";
    }

    @Transactional(readOnly = true)
    public List<DashboardAiTradeHistoryItem> aiTradeHistory(String filter, int limit) {
        String normalizedFilter = normalizeTradeHistoryFilter(filter);
        int normalizedLimit = normalizeTradeHistoryLimit(limit);
        return aiDecisionMapper.findTradeHistory(normalizedFilter, normalizedLimit);
    }

    private String normalizeTradeHistoryFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return "ALL";
        }
        String normalized = filter.trim().toUpperCase();
        if ("BUY".equals(normalized)
                || "SELL".equals(normalized)
                || "BUY_FILLED".equals(normalized)
                || "SELL_FILLED".equals(normalized)) {
            return normalized;
        }
        return "ALL";
    }

    private int normalizeTradeHistoryLimit(int limit) {
        if (limit <= 0) {
            return TRADE_HISTORY_DEFAULT_LIMIT;
        }
        return Math.min(limit, TRADE_HISTORY_MAX_LIMIT);
    }
}
