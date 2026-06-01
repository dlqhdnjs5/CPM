package com.bowon.cpm.admin;

import com.bowon.cpm.broker.dto.AccountBalanceResult;
import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.portfolio.domain.PortfolioPosition;
import com.bowon.cpm.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final PortfolioService portfolioService;

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
}

