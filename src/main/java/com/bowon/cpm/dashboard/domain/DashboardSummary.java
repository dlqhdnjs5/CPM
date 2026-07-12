package com.bowon.cpm.dashboard.domain;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.domain.ExternalApiCallLog;
import com.bowon.cpm.common.domain.SchedulerExecutionLog;
import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.domain.PaperPortfolioPosition;
import com.bowon.cpm.risk.domain.RiskCheckResult;
import com.bowon.cpm.stock.domain.StockMaster;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardSummary(
        LocalDateTime generatedAt,
        SystemStatus system,
        List<StockMaster> activeStocks,
        List<StockMaster> stockMasterList,
        PaperAccountBalance paperBalance,
        List<PaperPortfolioPosition> paperPositions,
        DecisionSummary decisions,
        List<AiDecision> recentDecisions,
        List<RiskCheckResult> recentRiskChecks,
        List<OrderRequest> recentOrders,
        List<SchedulerExecutionLog> recentSchedulers,
        List<ExternalApiCallLog> recentExternalApiFailures,
        List<BrokerApiLog> recentBrokerApiFailures
) {
    public record SystemStatus(
            String tradingMode,
            boolean tradingEnabled,
            String accountNoMasked,
            int activeStockCount
    ) {
    }

    public record DecisionSummary(
            long buyCount,
            long holdCount,
            long sellCount,
            long failedRiskCount
    ) {
    }
}
