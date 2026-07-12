package com.bowon.cpm.feedback.service;

import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.feedback.domain.PortfolioRealizedProfitLoss;
import com.bowon.cpm.feedback.mapper.PortfolioRealizedProfitLossMapper;
import com.bowon.cpm.paper.domain.PaperPortfolioProfitLoss;
import com.bowon.cpm.paper.mapper.PaperPortfolioProfitLossMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerformanceQueryService {

    private final KisProperties kisProperties;
    private final PaperPortfolioProfitLossMapper paperProfitLossMapper;
    private final PortfolioRealizedProfitLossMapper realizedProfitLossMapper;

    @Transactional(readOnly = true)
    public PaperPerformance getPaperPerformance(int limit) {
        String accountNo = kisProperties.accountNo();
        Map<String, Object> aggregate = realizedProfitLossMapper.aggregatePaperByAccountNo(accountNo);
        List<PortfolioRealizedProfitLoss> realized =
                realizedProfitLossMapper.findRecentPaperByAccountNo(accountNo, limit);
        List<PaperPortfolioProfitLoss> daily =
                paperProfitLossMapper.findRecentByAccountNo(accountNo, limit);
        return new PaperPerformance(accountNo, aggregate, realized, daily);
    }

    public record PaperPerformance(
            String accountNo,
            Map<String, Object> aggregate,
            List<PortfolioRealizedProfitLoss> recentRealizedProfitLoss,
            List<PaperPortfolioProfitLoss> recentDailyProfitLoss
    ) {}
}
