package com.bowon.cpm.paper.service;

import com.bowon.cpm.order.domain.OrderRequest;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.domain.PaperPortfolioPosition;
import com.bowon.cpm.paper.domain.PaperPortfolioProfitLoss;
import com.bowon.cpm.paper.mapper.PaperAccountBalanceMapper;
import com.bowon.cpm.paper.mapper.PaperPortfolioPositionMapper;
import com.bowon.cpm.paper.mapper.PaperPortfolioProfitLossMapper;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperPortfolioServiceTest {

    @Mock PaperAccountBalanceMapper accountBalanceMapper;
    @Mock PaperPortfolioPositionMapper positionMapper;
    @Mock PaperPortfolioProfitLossMapper profitLossMapper;
    @Mock StockService stockService;

    PaperPortfolioService service;

    @BeforeEach
    void setUp() {
        service = new PaperPortfolioService(
                accountBalanceMapper,
                positionMapper,
                profitLossMapper,
                stockService
        );
    }

    @Test
    @DisplayName("Initializes PAPER account with seed cash only once")
    void initializesPaperAccount() {
        when(accountBalanceMapper.findLatestByAccountNo("ACC")).thenReturn(List.of());

        service.ensureAccountInitialized("ACC", new BigDecimal("100000"));

        ArgumentCaptor<PaperAccountBalance> captor = ArgumentCaptor.forClass(PaperAccountBalance.class);
        verify(accountBalanceMapper).insert(captor.capture());
        assertThat(captor.getValue().getCashBalance()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(captor.getValue().getTotalAssetAmount()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    @Test
    @DisplayName("BUY execution updates PAPER position, cash, and daily P/L")
    void buyExecutionUpdatesPaperLedger() {
        PaperAccountBalance previous = balance("ACC", "100000", LocalDateTime.of(2026, 6, 5, 9, 0));
        PaperAccountBalance current = balance("ACC", "90000", "100000", LocalDateTime.of(2026, 6, 5, 9, 1));

        when(positionMapper.findByAccountNoAndStockCode("ACC", "005930")).thenReturn(Optional.empty());
        when(stockService.findByStockCode("005930")).thenReturn(Optional.of(
                StockMaster.builder().stockCode("005930").stockName("Samsung Electronics").build()
        ));
        when(accountBalanceMapper.findLatestByAccountNo("ACC"))
                .thenReturn(List.of(previous), List.of(current, previous));
        when(positionMapper.findAllHeld("ACC")).thenReturn(List.of(PaperPortfolioPosition.builder()
                .accountNo("ACC")
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .quantity(10)
                .availableQuantity(10)
                .averageBuyPrice(new BigDecimal("1000.0000"))
                .currentPrice(new BigDecimal("1000"))
                .purchaseAmount(new BigDecimal("10000.0000"))
                .valuationAmount(new BigDecimal("10000"))
                .profitLossAmount(BigDecimal.ZERO)
                .profitLossRate(BigDecimal.ZERO)
                .build()));

        service.applyExecution(
                OrderRequest.builder()
                        .accountNo("ACC")
                        .stockCode("005930")
                        .orderSide("BUY")
                        .orderQuantity(10)
                        .build(),
                new BigDecimal("1000"),
                new BigDecimal("10000")
        );

        ArgumentCaptor<PaperPortfolioPosition> positionCaptor =
                ArgumentCaptor.forClass(PaperPortfolioPosition.class);
        verify(positionMapper).upsert(positionCaptor.capture());
        assertThat(positionCaptor.getValue().getQuantity()).isEqualTo(10);
        assertThat(positionCaptor.getValue().getAverageBuyPrice()).isEqualByComparingTo(new BigDecimal("1000.0000"));
        assertThat(positionCaptor.getValue().getStockName()).isEqualTo("Samsung Electronics");

        ArgumentCaptor<PaperAccountBalance> balanceCaptor = ArgumentCaptor.forClass(PaperAccountBalance.class);
        verify(accountBalanceMapper).insert(balanceCaptor.capture());
        assertThat(balanceCaptor.getValue().getCashBalance()).isEqualByComparingTo(new BigDecimal("90000"));
        assertThat(balanceCaptor.getValue().getTotalAssetAmount()).isEqualByComparingTo(new BigDecimal("100000"));

        ArgumentCaptor<PaperPortfolioProfitLoss> profitCaptor =
                ArgumentCaptor.forClass(PaperPortfolioProfitLoss.class);
        verify(profitLossMapper).upsert(profitCaptor.capture());
        assertThat(profitCaptor.getValue().getEvaluationType()).isEqualTo("DAILY");
        assertThat(profitCaptor.getValue().getReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private PaperAccountBalance balance(String accountNo, String cash, LocalDateTime at) {
        return balance(accountNo, cash, cash, at);
    }

    private PaperAccountBalance balance(String accountNo, String cash, String totalAsset, LocalDateTime at) {
        return PaperAccountBalance.builder()
                .accountNo(accountNo)
                .baseDatetime(at)
                .cashBalance(new BigDecimal(cash))
                .availableCash(new BigDecimal(cash))
                .totalAssetAmount(new BigDecimal(totalAsset))
                .totalEvaluationAmount(BigDecimal.ZERO)
                .totalProfitLossAmount(BigDecimal.ZERO)
                .totalProfitLossRate(BigDecimal.ZERO)
                .build();
    }
}
