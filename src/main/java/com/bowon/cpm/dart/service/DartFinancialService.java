package com.bowon.cpm.dart.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.dart.client.DartFinancialClient;
import com.bowon.cpm.dart.client.dto.DartFinancialResponse;
import com.bowon.cpm.dart.client.dto.DartStockQuantityResponse;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.domain.DartFinancialStatement;
import com.bowon.cpm.dart.domain.DartStockQuantity;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
import com.bowon.cpm.dart.mapper.DartFinancialStatementMapper;
import com.bowon.cpm.dart.mapper.DartStockQuantityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartFinancialService {

    private static final String ANNUAL_REPORT = "11011";
    private static final String HALF_REPORT = "11012";

    private static final Set<String> KEY_ACCOUNT_IDS = Set.of(
            "ifrs-full_Revenue",
            "ifrs-full_RevenueFromContractsWithCustomers",
            "ifrs-full_RevenueFromContractsWithCustomersExcludingAssessedTax",
            "dart_OperatingIncomeLoss",
            "ifrs-full_ProfitLoss",
            "ifrs-full_ProfitLossAttributableToOwnersOfParent",
            "ifrs-full_Assets",
            "ifrs-full_Liabilities",
            "ifrs-full_Equity",
            "ifrs-full_GrossProfit",
            "ifrs-full_FinanceIncome",
            "ifrs-full_FinanceCosts"
    );

    private static final Set<String> KEY_ACCOUNT_NAME_TOKENS = Set.of(
            "\uB9E4\uCD9C\uC561",
            "\uC601\uC5C5\uC774\uC775",
            "\uB2F9\uAE30\uC21C\uC774\uC775",
            "\uC21C\uC774\uC775",
            "\uC790\uC0B0\uCD1D\uACC4",
            "\uBD80\uCC44\uCD1D\uACC4",
            "\uC790\uBCF8\uCD1D\uACC4",
            "\uB9E4\uCD9C\uCD1D\uC774\uC775",
            "\uAE08\uC735\uC218\uC775",
            "\uAE08\uC735\uBE44\uC6A9",
            "revenue",
            "sales",
            "operating income",
            "operating profit",
            "profit",
            "assets",
            "liabilities",
            "equity"
    );

    private final DartFinancialClient dartFinancialClient;
    private final DartFinancialStatementMapper financialStatementMapper;
    private final DartStockQuantityMapper stockQuantityMapper;
    private final DartCorpCodeMapper corpCodeMapper;
    private final OpenAiDecisionClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public int fetchAndSave(String stockCode) {
        DartCorpCode corpCodeInfo = corpCodeMapper.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("corp_code not found: stockCode=" + stockCode));
        String corpCode = corpCodeInfo.getCorpCode();

        int currentYear = LocalDate.now().getYear();
        int savedCount = 0;

        for (int year = currentYear - 1; year >= currentYear - 2; year--) {
            savedCount += fetchForYear(stockCode, corpCode, year, ANNUAL_REPORT);
        }
        savedCount += fetchForYear(stockCode, corpCode, currentYear, HALF_REPORT);

        log.info("[DartFinancial] financial statements fetched: stockCode={}, savedCount={}", stockCode, savedCount);
        return savedCount;
    }

    @Transactional
    public int fetchAndSaveStockQuantity(String stockCode) {
        DartCorpCode corpCodeInfo = corpCodeMapper.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("corp_code not found: stockCode=" + stockCode));

        int currentYear = LocalDate.now().getYear();
        int savedCount = 0;
        for (int year = currentYear - 1; year >= currentYear - 2; year--) {
            savedCount += fetchStockQuantityForYear(stockCode, corpCodeInfo.getCorpCode(), year, ANNUAL_REPORT);
        }

        log.info("[DartFinancial] stock quantity fetched: stockCode={}, savedCount={}", stockCode, savedCount);
        return savedCount;
    }

    @Transactional(readOnly = true)
    public List<DartStockQuantity> getStockQuantities(String stockCode) {
        return stockQuantityMapper.findByStockCode(stockCode, 20);
    }

    private int fetchForYear(String stockCode, String corpCode, int year, String reportCode) {
        try {
            DartFinancialResponse response = dartFinancialClient.getFinancialStatement(corpCode, year, reportCode);

            if (response.isEmpty() || !response.isSuccess() || response.list() == null) {
                log.debug("[DartFinancial] no financial data: corpCode={}, year={}, report={}", corpCode, year, reportCode);
                return 0;
            }

            int count = 0;
            for (DartFinancialResponse.FinancialItem item : response.list()) {
                if (!isKeyAccount(item)) {
                    continue;
                }
                if (!"CFS".equals(item.fsDiv()) && !"OFS".equals(item.fsDiv())) {
                    continue;
                }
                if ("OFS".equals(item.fsDiv()) && hasConsolidated(response.list(), item.accountName())) {
                    continue;
                }

                DartFinancialStatement stmt = DartFinancialStatement.builder()
                        .corpCode(corpCode)
                        .stockCode(stockCode)
                        .businessYear(year)
                        .reportCode(reportCode)
                        .statementType(item.fsDiv() + "_" + item.statementDiv())
                        .accountId(item.accountId())
                        .accountName(trimOrNull(item.accountName()))
                        .amount(parseBigDecimal(item.currentAmount()))
                        .currency(item.currency() != null ? item.currency() : "KRW")
                        .rawJson(toJson(item))
                        .build();

                financialStatementMapper.insertIgnore(stmt);
                count++;
            }
            return count;

        } catch (Exception e) {
            log.warn("[DartFinancial] financial fetch failed and skipped: corpCode={}, year={}, report={}, error={}",
                    corpCode, year, reportCode, e.getMessage());
            return 0;
        }
    }

    private int fetchStockQuantityForYear(String stockCode, String corpCode, int year, String reportCode) {
        try {
            DartStockQuantityResponse response = dartFinancialClient.getStockTotalQuantity(corpCode, year, reportCode);

            if (response.isEmpty() || !response.isSuccess() || response.list() == null) {
                log.debug("[DartFinancial] no stock quantity data: corpCode={}, year={}, report={}", corpCode, year, reportCode);
                return 0;
            }

            int count = 0;
            for (DartStockQuantityResponse.StockQuantityItem item : response.list()) {
                DartStockQuantity quantity = DartStockQuantity.builder()
                        .corpCode(corpCode)
                        .stockCode(stockCode)
                        .businessYear(year)
                        .reportCode(reportCode)
                        .stockType(defaultStockType(item.stockType()))
                        .issuedStockQuantity(parseLong(item.issuedStockQuantity()))
                        .treasuryStockQuantity(parseLong(item.treasuryStockQuantity()))
                        .distributedStockQuantity(parseLong(item.distributedStockQuantity()))
                        .settlementDate(parseDate(item.settlementDate()))
                        .rawJson(toJson(item))
                        .build();
                stockQuantityMapper.upsert(quantity);
                count++;
            }
            return count;
        } catch (Exception e) {
            log.warn("[DartFinancial] stock quantity fetch failed and skipped: corpCode={}, year={}, report={}, error={}",
                    corpCode, year, reportCode, e.getMessage());
            return 0;
        }
    }

    private boolean isKeyAccount(DartFinancialResponse.FinancialItem item) {
        if (item == null) {
            return false;
        }
        String accountId = trimOrNull(item.accountId());
        if (accountId != null && KEY_ACCOUNT_IDS.contains(accountId)) {
            return true;
        }

        String accountName = trimOrNull(item.accountName());
        if (accountName == null) {
            return false;
        }
        String lower = accountName.toLowerCase(Locale.ROOT);
        return KEY_ACCOUNT_NAME_TOKENS.stream().anyMatch(lower::contains);
    }

    private boolean hasConsolidated(List<DartFinancialResponse.FinancialItem> list, String accountName) {
        if (accountName == null) {
            return false;
        }
        String target = accountName.trim();
        return list.stream().anyMatch(item ->
                "CFS".equals(item.fsDiv())
                        && target.equals(item.accountName() != null ? item.accountName().trim() : ""));
    }

    public String summarize(String stockCode) {
        List<DartFinancialStatement> statements = financialStatementMapper.findByStockCode(stockCode, 30);
        if (statements.isEmpty()) {
            return null;
        }

        StringBuilder body = new StringBuilder();
        body.append("Summarize the following financial data in two concise Korean lines. Amounts are KRW.\n\n");
        statements.stream()
                .filter(statement -> statement.getAmount() != null)
                .forEach(statement -> body.append(String.format("[%d %s] %s: %,.0f%n",
                        statement.getBusinessYear(),
                        statement.getReportCode(),
                        statement.getAccountName(),
                        statement.getAmount())));

        try {
            OpenAiResponse response = openAiClient.createTextCompletion(
                    "You are a financial analyst. Summarize only the key trend and risk.",
                    body.toString(),
                    openAiProperties.modelSummary()
            );
            String text = response.extractText();
            return text != null ? text.trim() : null;
        } catch (Exception e) {
            log.warn("[DartFinancial] OpenAI summary failed: {}", e.getMessage());
            return buildSimpleSummary(statements);
        }
    }

    private String buildSimpleSummary(List<DartFinancialStatement> statements) {
        StringBuilder sb = new StringBuilder();
        statements.stream()
                .filter(statement -> statement.getAmount() != null)
                .limit(6)
                .forEach(statement -> sb.append(String.format("[%d] %s: %,.0f | ",
                        statement.getBusinessYear(), statement.getAccountName(), statement.getAmount())));
        return sb.length() > 0 ? sb.toString() : null;
    }

    @Transactional(readOnly = true)
    public List<DartFinancialStatement> getStatements(String stockCode) {
        return financialStatementMapper.findByStockCode(stockCode, 30);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            String normalized = value.replace(",", "").trim();
            if (normalized.startsWith("(") && normalized.endsWith(")")) {
                normalized = "-" + normalized.substring(1, normalized.length() - 1);
            }
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        BigDecimal decimal = parseBigDecimal(value);
        return decimal != null ? decimal.longValue() : null;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultStockType(String value) {
        String trimmed = trimOrNull(value);
        return trimmed != null ? trimmed : "UNKNOWN";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
