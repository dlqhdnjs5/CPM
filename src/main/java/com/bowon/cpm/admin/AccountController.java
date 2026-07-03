package com.bowon.cpm.admin;

import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.domain.PaperPortfolioPosition;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final PortfolioService portfolioService;
    private final PaperPortfolioService paperPortfolioService;
    private final KisProperties kisProperties;

    /**
     * 계좌 잔고 조회 + DB 저장
     * GET /api/account/balance
     */
    @GetMapping("/balance")
    public ApiResponse<AccountBalanceResult> getBalance() {
        AccountBalanceResult result = portfolioService.syncAccountBalance();
        return ApiResponse.ok(result);
    }

    /**
     * 현재 보유 종목 조회
     * GET /api/account/positions
     */
    @GetMapping("/positions")
    public ApiResponse<List<PortfolioPosition>> getPositions() {
        List<PortfolioPosition> positions = portfolioService.getPositions();
        return ApiResponse.ok(positions);
    }

    @GetMapping("/paper/balance")
    public ApiResponse<PaperAccountBalance> getPaperBalance() {
        return paperPortfolioService.findLatestAccountBalance(kisProperties.accountNo())
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("PAPER account balance not initialized"));
    }

    @PostMapping("/paper/balance/init")
    public ApiResponse<PaperAccountBalance> initializePaperBalance(@RequestParam BigDecimal seedCash) {
        String accountNo = kisProperties.accountNo();
        paperPortfolioService.ensureAccountInitialized(accountNo, seedCash);
        return paperPortfolioService.findLatestAccountBalance(accountNo)
                .map(balance -> ApiResponse.ok("PAPER account balance initialized", balance))
                .orElse(ApiResponse.error("PAPER account balance initialization failed"));
    }

    @GetMapping("/paper/positions")
    public ApiResponse<List<PaperPortfolioPosition>> getPaperPositions() {
        return ApiResponse.ok(paperPortfolioService.findPositions(kisProperties.accountNo()));
    }
}

