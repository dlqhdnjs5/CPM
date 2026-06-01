package com.bowon.cpm.dart.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.dart.client.DartFinancialClient;
import com.bowon.cpm.dart.client.dto.DartFinancialResponse;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.domain.DartFinancialStatement;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
import com.bowon.cpm.dart.mapper.DartFinancialStatementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * DART 재무제표 수집 서비스
 *
 * 1. stock_code → corp_code 조회
 * 2. 연도별 사업보고서 재무제표 수집
 * 3. 핵심 계정만 필터링하여 저장
 * 4. OpenAI로 재무 요약 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartFinancialService {

    private final DartFinancialClient dartFinancialClient;
    private final DartFinancialStatementMapper financialStatementMapper;
    private final DartCorpCodeMapper corpCodeMapper;
    private final OpenAiDecisionClient openAiClient;
    private final OpenAiProperties openAiProperties;

    /** 수집할 핵심 계정명 (한글 account_nm 기준) */
    private static final Set<String> KEY_ACCOUNTS = Set.of(
            "매출액", "영업이익", "당기순이익",
            "자산총계", "부채총계", "자본총계",
            "매출총이익", "금융수익", "금융비용"
    );

    /** 수집할 보고서 코드 */
    private static final String ANNUAL_REPORT = "11011"; // 사업보고서
    private static final String HALF_REPORT   = "11012"; // 반기보고서

    /**
     * 종목코드 기준 재무제표 수집 (최근 2개년)
     *
     * @return 저장된 건수
     */
    @Transactional
    public int fetchAndSave(String stockCode) {
        // 1. corp_code 조회
        DartCorpCode corpCodeInfo = corpCodeMapper.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("corp_code 없음: stockCode=" + stockCode));
        String corpCode = corpCodeInfo.getCorpCode();

        int currentYear = java.time.LocalDate.now().getYear();
        int savedCount = 0;

        // 2. 최근 2개년 사업보고서 + 최근 반기 수집
        for (int year = currentYear - 1; year >= currentYear - 2; year--) {
            savedCount += fetchForYear(stockCode, corpCode, year, ANNUAL_REPORT);
        }
        // 올해 반기 시도 (없으면 조용히 스킵)
        savedCount += fetchForYear(stockCode, corpCode, currentYear, HALF_REPORT);

        log.info("[DartFinancial] 재무제표 수집 완료: stockCode={}, 총 {}건", stockCode, savedCount);
        return savedCount;
    }

    /**
     * 특정 연도/보고서 코드 재무제표 수집
     */
    private int fetchForYear(String stockCode, String corpCode, int year, String reportCode) {
        try {
            DartFinancialResponse response = dartFinancialClient.getFinancialStatement(corpCode, year, reportCode);

            if (response.isEmpty() || !response.isSuccess() || response.list() == null) {
                log.debug("[DartFinancial] 데이터 없음: corpCode={}, year={}, report={}", corpCode, year, reportCode);
                return 0;
            }

            int count = 0;
            for (DartFinancialResponse.FinancialItem item : response.list()) {
                // 핵심 계정만 저장 (연결재무제표 우선)
                if (item.accountName() == null) continue;
                if (!KEY_ACCOUNTS.contains(item.accountName().trim())) continue;
                if (!"CFS".equals(item.fsDiv()) && !"OFS".equals(item.fsDiv())) continue;
                // 연결 있으면 별도 스킵
                if ("OFS".equals(item.fsDiv()) && hasConsolidated(response.list(), item.accountName())) continue;

                DartFinancialStatement stmt = DartFinancialStatement.builder()
                        .corpCode(corpCode)
                        .stockCode(stockCode)
                        .businessYear(year)
                        .reportCode(reportCode)
                        .statementType(item.fsDiv() + "_" + item.statementDiv())
                        .accountId(item.accountId())
                        .accountName(item.accountName().trim())
                        .amount(parseBigDecimal(item.currentAmount()))
                        .currency(item.currency() != null ? item.currency() : "KRW")
                        .rawJson(null)
                        .build();

                financialStatementMapper.insertIgnore(stmt);
                count++;
            }
            return count;

        } catch (Exception e) {
            log.warn("[DartFinancial] 수집 실패 (스킵): corpCode={}, year={}, report={}, error={}",
                    corpCode, year, reportCode, e.getMessage());
            return 0;
        }
    }

    /**
     * 연결재무제표 데이터가 있으면 별도는 제외
     */
    private boolean hasConsolidated(List<DartFinancialResponse.FinancialItem> list, String accountName) {
        return list.stream().anyMatch(i ->
                "CFS".equals(i.fsDiv()) && accountName.trim().equals(
                        i.accountName() != null ? i.accountName().trim() : ""));
    }

    /**
     * 재무제표 데이터를 OpenAI로 요약
     * 프롬프트에 넣을 텍스트 반환
     */
    public String summarize(String stockCode) {
        List<DartFinancialStatement> statements = financialStatementMapper.findByStockCode(stockCode, 30);
        if (statements.isEmpty()) return null;

        // 텍스트로 정리
        StringBuilder sb = new StringBuilder();
        sb.append("아래 재무 데이터를 2줄로 핵심만 요약하라. 숫자는 억원 단위로 변환하라.\n\n");
        statements.stream()
                .filter(s -> s.getAmount() != null)
                .forEach(s -> sb.append(String.format("[%d년 %s] %s: %,.0f원\n",
                        s.getBusinessYear(), s.getReportCode(),
                        s.getAccountName(), s.getAmount())));

        try {
            OpenAiResponse response = openAiClient.createTextCompletion(
                    "너는 재무 분석 전문가다. 핵심 지표만 2줄로 요약한다. 참고로 현재 날짜는" + LocalDate.now() + " 다",
                    sb.toString(),
                    openAiProperties.modelSummary()
            );
            String text = response.extractText();
            return text != null ? text.trim() : null;
        } catch (Exception e) {
            log.warn("[DartFinancial] OpenAI 요약 실패: {}", e.getMessage());
            return buildSimpleSummary(statements);
        }
    }

    /**
     * OpenAI 실패 시 단순 텍스트 요약 (fallback)
     */
    private String buildSimpleSummary(List<DartFinancialStatement> statements) {
        StringBuilder sb = new StringBuilder();
        statements.stream()
                .filter(s -> s.getAmount() != null)
                .limit(6)
                .forEach(s -> sb.append(String.format("[%d년] %s: %,.0f원 | ",
                        s.getBusinessYear(), s.getAccountName(), s.getAmount())));
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 저장된 재무제표 조회
     */
    @Transactional(readOnly = true)
    public List<DartFinancialStatement> getStatements(String stockCode) {
        return financialStatementMapper.findByStockCode(stockCode, 30);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}


