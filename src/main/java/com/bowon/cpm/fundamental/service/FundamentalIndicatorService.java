package com.bowon.cpm.fundamental.service;

import com.bowon.cpm.dart.domain.DartFinancialStatement;
import com.bowon.cpm.dart.domain.DartStockQuantity;
import com.bowon.cpm.dart.mapper.DartFinancialStatementMapper;
import com.bowon.cpm.dart.mapper.DartStockQuantityMapper;
import com.bowon.cpm.fundamental.domain.StockFundamentalIndicator;
import com.bowon.cpm.fundamental.mapper.StockFundamentalIndicatorMapper;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundamentalIndicatorService {

    private static final int STATEMENT_LIMIT = 200;

    private final DartFinancialStatementMapper financialStatementMapper;
    private final DartStockQuantityMapper stockQuantityMapper;
    private final StockPriceDailyMapper priceDailyMapper;
    private final StockFundamentalIndicatorMapper indicatorMapper;
    private final StockMasterMapper stockMasterMapper;
    private final FundamentalIndicatorCalculator calculator;

    @Transactional
    public StockFundamentalIndicator calculateAndSave(String stockCode) {
        List<DartFinancialStatement> statements = financialStatementMapper.findByStockCode(stockCode, STATEMENT_LIMIT);
        if (statements.isEmpty()) {
            throw new IllegalStateException("financial statements not found: stockCode=" + stockCode);
        }

        List<FundamentalIndicatorCalculator.FinancialSnapshot> snapshots = buildSnapshots(statements);
        if (snapshots.isEmpty()) {
            throw new IllegalStateException("usable financial statement snapshot not found: stockCode=" + stockCode);
        }

        FundamentalIndicatorCalculator.FinancialSnapshot current = snapshots.get(0);
        FundamentalIndicatorCalculator.FinancialSnapshot previous = snapshots.stream()
                .filter(snapshot -> snapshot.businessYear() != null && current.businessYear() != null)
                .filter(snapshot -> snapshot.businessYear() < current.businessYear())
                .findFirst()
                .orElse(null);

        DartStockQuantity stockQuantity = stockQuantityMapper.findLatestByStockCode(stockCode).orElse(null);
        StockPriceDaily latestPrice = priceDailyMapper.findLatestByStockCode(stockCode);

        StockFundamentalIndicator indicator = calculator.calculate(
                stockCode,
                current,
                previous,
                stockQuantity,
                latestPrice
        );
        indicatorMapper.upsert(indicator);
        return indicator;
    }

    @Transactional
    public int calculateAndSaveAllActive() {
        int count = 0;
        for (StockMaster stock : stockMasterMapper.findAllActive()) {
            try {
                calculateAndSave(stock.getStockCode());
                count++;
            } catch (Exception e) {
                log.warn("[Fundamental] calculate failed: stockCode={}, error={}", stock.getStockCode(), e.getMessage());
            }
        }
        return count;
    }

    @Transactional(readOnly = true)
    public Optional<StockFundamentalIndicator> findLatest(String stockCode) {
        return indicatorMapper.findLatestByStockCode(stockCode);
    }

    @Transactional(readOnly = true)
    public List<StockFundamentalIndicator> findByStockCode(String stockCode, int limit) {
        int normalizedLimit = limit > 0 ? Math.min(limit, 100) : 20;
        return indicatorMapper.findByStockCode(stockCode, normalizedLimit);
    }

    private List<FundamentalIndicatorCalculator.FinancialSnapshot> buildSnapshots(List<DartFinancialStatement> statements) {
        Map<PeriodKey, List<DartFinancialStatement>> byPeriod = statements.stream()
                .filter(statement -> statement.getBusinessYear() != null)
                .filter(statement -> statement.getReportCode() != null)
                .collect(Collectors.groupingBy(statement ->
                        new PeriodKey(statement.getBusinessYear(), statement.getReportCode())));

        return byPeriod.entrySet().stream()
                .map(entry -> toSnapshot(entry.getKey(), entry.getValue()))
                .filter(this::hasUsableData)
                .sorted(this::compareSnapshot)
                .toList();
    }

    private FundamentalIndicatorCalculator.FinancialSnapshot toSnapshot(
            PeriodKey key,
            List<DartFinancialStatement> statements
    ) {
        String corpCode = statements.stream()
                .map(DartFinancialStatement::getCorpCode)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        return new FundamentalIndicatorCalculator.FinancialSnapshot(
                key.businessYear(),
                key.reportCode(),
                corpCode,
                valueOf(statements, AccountMetric.REVENUE),
                valueOf(statements, AccountMetric.OPERATING_INCOME),
                valueOf(statements, AccountMetric.NET_INCOME),
                valueOf(statements, AccountMetric.TOTAL_ASSETS),
                valueOf(statements, AccountMetric.TOTAL_LIABILITIES),
                valueOf(statements, AccountMetric.TOTAL_EQUITY)
        );
    }

    private BigDecimal valueOf(List<DartFinancialStatement> statements, AccountMetric metric) {
        return statements.stream()
                .filter(statement -> statement.getAmount() != null)
                .filter(statement -> matches(statement, metric))
                .min(Comparator.comparingInt(this::financialStatementPriority))
                .map(DartFinancialStatement::getAmount)
                .orElse(null);
    }

    private boolean matches(DartFinancialStatement statement, AccountMetric metric) {
        String accountId = trim(statement.getAccountId());
        if (accountId != null && metric.accountIds().contains(accountId)) {
            return true;
        }

        String accountName = trim(statement.getAccountName());
        if (accountName == null) {
            return false;
        }
        String lower = accountName.toLowerCase(Locale.ROOT);
        return metric.nameTokens().stream().anyMatch(lower::contains);
    }

    private int financialStatementPriority(DartFinancialStatement statement) {
        String statementType = statement.getStatementType();
        if (statementType != null && statementType.startsWith("CFS")) {
            return 0;
        }
        return 1;
    }

    private boolean hasUsableData(FundamentalIndicatorCalculator.FinancialSnapshot snapshot) {
        return snapshot.revenue() != null
                || snapshot.operatingIncome() != null
                || snapshot.netIncome() != null
                || snapshot.totalAssets() != null
                || snapshot.totalLiabilities() != null
                || snapshot.totalEquity() != null;
    }

    private int compareSnapshot(FundamentalIndicatorCalculator.FinancialSnapshot left,
                                FundamentalIndicatorCalculator.FinancialSnapshot right) {
        int yearCompare = Integer.compare(nullSafe(right.businessYear()), nullSafe(left.businessYear()));
        if (yearCompare != 0) {
            return yearCompare;
        }
        return Integer.compare(reportPriority(left.reportCode()), reportPriority(right.reportCode()));
    }

    private int reportPriority(String reportCode) {
        if ("11011".equals(reportCode)) {
            return 0;
        }
        if ("11014".equals(reportCode)) {
            return 1;
        }
        if ("11012".equals(reportCode)) {
            return 2;
        }
        if ("11013".equals(reportCode)) {
            return 3;
        }
        return 9;
    }

    private int nullSafe(Integer value) {
        return value != null ? value : 0;
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record PeriodKey(Integer businessYear, String reportCode) {
    }

    private enum AccountMetric {
        REVENUE(
                Set.of(
                        "ifrs-full_Revenue",
                        "ifrs-full_RevenueFromContractsWithCustomers",
                        "ifrs-full_RevenueFromContractsWithCustomersExcludingAssessedTax"
                ),
                Set.of("revenue", "sales", "\uB9E4\uCD9C\uC561")
        ),
        OPERATING_INCOME(
                Set.of("dart_OperatingIncomeLoss"),
                Set.of("operating income", "operating profit", "\uC601\uC5C5\uC774\uC775")
        ),
        NET_INCOME(
                Set.of(
                        "ifrs-full_ProfitLoss",
                        "ifrs-full_ProfitLossAttributableToOwnersOfParent"
                ),
                Set.of(
                        "net income",
                        "profit loss",
                        "profit for the year",
                        "\uB2F9\uAE30\uC21C\uC774\uC775",
                        "\uC21C\uC774\uC775",
                        "\uB2F9\uAE30\uC21C\uC190\uC775"
                )
        ),
        TOTAL_ASSETS(
                Set.of("ifrs-full_Assets"),
                Set.of("assets", "\uC790\uC0B0\uCD1D\uACC4")
        ),
        TOTAL_LIABILITIES(
                Set.of("ifrs-full_Liabilities"),
                Set.of("liabilities", "\uBD80\uCC44\uCD1D\uACC4")
        ),
        TOTAL_EQUITY(
                Set.of("ifrs-full_Equity"),
                Set.of("equity", "\uC790\uBCF8\uCD1D\uACC4")
        );

        private final Set<String> accountIds;
        private final Set<String> nameTokens;

        AccountMetric(Set<String> accountIds, Set<String> nameTokens) {
            this.accountIds = accountIds;
            this.nameTokens = nameTokens;
        }

        private Set<String> accountIds() {
            return accountIds;
        }

        private Set<String> nameTokens() {
            return nameTokens;
        }
    }
}
