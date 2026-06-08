package com.bowon.cpm.fundamental.service;

import com.bowon.cpm.dart.domain.DartFinancialStatement;
import com.bowon.cpm.dart.domain.DartStockQuantity;
import com.bowon.cpm.dart.mapper.DartFinancialStatementMapper;
import com.bowon.cpm.dart.mapper.DartStockQuantityMapper;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.fundamental.mapper.StockFundamentalIndicatorMapper;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundamentalIndicatorServiceTest {

    @Test
    @DisplayName("calculates and saves latest annual fundamentals from DART statement rows")
    void calculateAndSave() {
        DartFinancialStatementMapper statementMapper = mock(DartFinancialStatementMapper.class);
        DartStockQuantityMapper stockQuantityMapper = mock(DartStockQuantityMapper.class);
        StockPriceDailyMapper priceDailyMapper = mock(StockPriceDailyMapper.class);
        StockFundamentalIndicatorMapper indicatorMapper = mock(StockFundamentalIndicatorMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);

        FundamentalIndicatorService service = new FundamentalIndicatorService(
                statementMapper,
                stockQuantityMapper,
                priceDailyMapper,
                indicatorMapper,
                stockMasterMapper,
                new FundamentalIndicatorCalculator()
        );

        when(statementMapper.findByStockCode("005930", 200)).thenReturn(List.of(
                stmt(2025, "11011", "ifrs-full_Revenue", "Revenue", "1000000000"),
                stmt(2025, "11011", "dart_OperatingIncomeLoss", "Operating Income", "150000000"),
                stmt(2025, "11011", "ifrs-full_ProfitLoss", "Profit", "100000000"),
                stmt(2025, "11011", "ifrs-full_Assets", "Assets", "2000000000"),
                stmt(2025, "11011", "ifrs-full_Liabilities", "Liabilities", "700000000"),
                stmt(2025, "11011", "ifrs-full_Equity", "Equity", "1300000000"),
                stmt(2024, "11011", "ifrs-full_Revenue", "Revenue", "800000000"),
                stmt(2024, "11011", "dart_OperatingIncomeLoss", "Operating Income", "100000000"),
                stmt(2024, "11011", "ifrs-full_ProfitLoss", "Profit", "70000000")
        ));
        when(stockQuantityMapper.findLatestByStockCode("005930")).thenReturn(Optional.of(DartStockQuantity.builder()
                .issuedStockQuantity(1_000_000L)
                .distributedStockQuantity(900_000L)
                .build()));
        when(priceDailyMapper.findLatestByStockCode("005930")).thenReturn(StockPriceDaily.builder()
                .stockCode("005930")
                .tradeDate(LocalDate.of(2026, 6, 5))
                .closePrice(new BigDecimal("1000"))
                .build());

        StockFundamentalIndicator result = service.calculateAndSave("005930");

        assertThat(result.getBusinessYear()).isEqualTo(2025);
        assertThat(result.getPer()).isEqualByComparingTo("10.000000");
        assertThat(result.getPbr()).isEqualByComparingTo("0.769231");
        assertThat(result.getRevenueGrowthRate()).isEqualByComparingTo("25.000000");

        ArgumentCaptor<StockFundamentalIndicator> captor =
                ArgumentCaptor.forClass(StockFundamentalIndicator.class);
        verify(indicatorMapper).upsert(captor.capture());
        assertThat(captor.getValue().getStockCode()).isEqualTo("005930");
    }

    @Test
    @DisplayName("matches Korean account names when DART account_id is missing")
    void calculateWithKoreanAccountNames() {
        DartFinancialStatementMapper statementMapper = mock(DartFinancialStatementMapper.class);
        DartStockQuantityMapper stockQuantityMapper = mock(DartStockQuantityMapper.class);
        StockPriceDailyMapper priceDailyMapper = mock(StockPriceDailyMapper.class);
        StockFundamentalIndicatorMapper indicatorMapper = mock(StockFundamentalIndicatorMapper.class);
        StockMasterMapper stockMasterMapper = mock(StockMasterMapper.class);

        FundamentalIndicatorService service = new FundamentalIndicatorService(
                statementMapper,
                stockQuantityMapper,
                priceDailyMapper,
                indicatorMapper,
                stockMasterMapper,
                new FundamentalIndicatorCalculator()
        );

        when(statementMapper.findByStockCode("005930", 200)).thenReturn(List.of(
                stmt(2025, "11011", null, "\uB9E4\uCD9C\uC561", "333605938000000"),
                stmt(2025, "11011", null, "\uC601\uC5C5\uC774\uC775", "43601051000000"),
                stmt(2025, "11011", null, "\uC790\uC0B0\uCD1D\uACC4", "566942110000000"),
                stmt(2025, "11011", null, "\uBD80\uCC44\uCD1D\uACC4", "130621773000000"),
                stmt(2025, "11011", null, "\uC790\uBCF8\uCD1D\uACC4", "436320337000000"),
                stmt(2024, "11011", null, "\uB9E4\uCD9C\uC561", "300870903000000"),
                stmt(2024, "11011", null, "\uC601\uC5C5\uC774\uC775", "32725961000000")
        ));
        when(stockQuantityMapper.findLatestByStockCode("005930")).thenReturn(Optional.of(DartStockQuantity.builder()
                .issuedStockQuantity(5_919_637_922L)
                .distributedStockQuantity(5_827_808_935L)
                .build()));
        when(priceDailyMapper.findLatestByStockCode("005930")).thenReturn(StockPriceDaily.builder()
                .stockCode("005930")
                .tradeDate(LocalDate.of(2026, 6, 5))
                .closePrice(new BigDecimal("60000"))
                .build());

        StockFundamentalIndicator result = service.calculateAndSave("005930");

        assertThat(result.getRevenue()).isEqualByComparingTo("333605938000000");
        assertThat(result.getOperatingIncome()).isEqualByComparingTo("43601051000000");
        assertThat(result.getTotalAssets()).isEqualByComparingTo("566942110000000");
        assertThat(result.getDebtRatio()).isEqualByComparingTo("29.937122");
        assertThat(result.getPbr()).isNotNull();
        verify(indicatorMapper).upsert(org.mockito.ArgumentMatchers.any());
    }

    private DartFinancialStatement stmt(int year, String reportCode, String accountId, String accountName, String amount) {
        return DartFinancialStatement.builder()
                .corpCode("00126380")
                .stockCode("005930")
                .businessYear(year)
                .reportCode(reportCode)
                .statementType("CFS_IS")
                .accountId(accountId)
                .accountName(accountName)
                .amount(new BigDecimal(amount))
                .currency("KRW")
                .build();
    }
}
