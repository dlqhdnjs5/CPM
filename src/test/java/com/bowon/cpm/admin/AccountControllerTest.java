package com.bowon.cpm.admin;

import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.service.PortfolioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired MockMvc mvc;

    @MockBean PortfolioService portfolioService;
    @MockBean PaperPortfolioService paperPortfolioService;
    @MockBean KisProperties kisProperties;

    @Test
    @DisplayName("POST /api/account/paper/balance/init initializes PAPER balance")
    void initializePaperBalance() throws Exception {
        when(kisProperties.accountNo()).thenReturn("12345678");
        when(paperPortfolioService.findLatestAccountBalance("12345678"))
                .thenReturn(Optional.of(PaperAccountBalance.builder()
                        .accountNo("12345678")
                        .baseDatetime(LocalDateTime.of(2026, 7, 3, 9, 0))
                        .cashBalance(new BigDecimal("10000000"))
                        .availableCash(new BigDecimal("10000000"))
                        .totalAssetAmount(new BigDecimal("10000000"))
                        .totalEvaluationAmount(BigDecimal.ZERO)
                        .totalProfitLossAmount(BigDecimal.ZERO)
                        .totalProfitLossRate(BigDecimal.ZERO)
                        .build()));

        mvc.perform(post("/api/account/paper/balance/init")
                        .param("seedCash", "10000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNo").value("12345678"))
                .andExpect(jsonPath("$.data.cashBalance").value(10000000));

        verify(paperPortfolioService).ensureAccountInitialized("12345678", new BigDecimal("10000000"));
    }
}
