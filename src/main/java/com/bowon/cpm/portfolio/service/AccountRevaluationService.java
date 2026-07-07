package com.bowon.cpm.portfolio.service;

import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountRevaluationService {

    private final TradingProperties tradingProperties;
    private final KisProperties kisProperties;
    private final PaperPortfolioService paperPortfolioService;
    private final PortfolioService portfolioService;

    public Map<String, Object> revalueCurrentMode() {
        if (tradingProperties.isPaperMode()) {
            PaperAccountBalance balance = paperPortfolioService.revalue(kisProperties.accountNo());
            return Map.of(
                    "mode", "PAPER",
                    "balance", balance
            );
        }

        AccountBalanceResult balance = portfolioService.syncAccountBalance();
        return Map.of(
                "mode", tradingProperties.normalizedMode(),
                "balance", balance
        );
    }

    public PaperAccountBalance revaluePaper() {
        return paperPortfolioService.revalue(kisProperties.accountNo());
    }
}
