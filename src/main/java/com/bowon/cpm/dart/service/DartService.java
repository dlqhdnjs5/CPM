package com.bowon.cpm.dart.service;

import com.bowon.cpm.common.domain.ExternalApiCallLog;
import com.bowon.cpm.common.mapper.ExternalApiCallLogMapper;
import com.bowon.cpm.dart.client.DartCorpCodeClient;
import com.bowon.cpm.dart.client.DartDisclosureClient;
import com.bowon.cpm.dart.client.dto.DartDisclosureResponse;
import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.mapper.DartCorpCodeMapper;
import com.bowon.cpm.dart.mapper.DartDisclosureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartService {

    private final DartCorpCodeClient corpCodeClient;
    private final DartDisclosureClient disclosureClient;
    private final DartCorpCodeMapper dartCorpCodeMapper;
    private final DartDisclosureMapper dartDisclosureMapper;
    private final ExternalApiCallLogMapper externalApiCallLogMapper;

    private static final DateTimeFormatter DART_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * DART corp_code 전체 수집 및 저장 (배치 1000건씩)
     *
     * @return 저장된 건수
     */
    @Transactional
    public int syncCorpCodes() {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            List<DartCorpCode> allCodes = corpCodeClient.fetchCorpCodes();

            // 1000건씩 배치 INSERT
            int batchSize = 1000;
            int total = 0;
            for (int i = 0; i < allCodes.size(); i += batchSize) {
                List<DartCorpCode> batch = allCodes.subList(i,
                        Math.min(i + batchSize, allCodes.size()));
                dartCorpCodeMapper.insertBatch(batch);
                total += batch.size();
            }

            success = true;
            log.info("[DART] corp_code 동기화 완료: total={}", total);
            return total;

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[DART] corp_code 동기화 실패: {}", errorMessage);
            throw e;
        } finally {
            saveApiLog("corpCode수집", "/api/corpCode.xml", success, errorMessage,
                    System.currentTimeMillis() - start);
        }
    }

    /**
     * 종목 코드 기반 공시 목록 수집
     * 1. stock_code → corp_code 조회
     * 2. DART 공시 목록 API 호출
     * 3. dart_disclosure INSERT IGNORE
     *
     * @param stockCode 종목 코드
     * @param fromDate  시작일
     * @param toDate    종료일
     * @return 저장된 공시 수
     */
    @Transactional
    public int fetchDisclosures(String stockCode, LocalDate fromDate, LocalDate toDate) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            // 1. corp_code 조회
            Optional<DartCorpCode> corpCodeOpt = dartCorpCodeMapper.findByStockCode(stockCode);
            if (corpCodeOpt.isEmpty()) {
                log.warn("[DART] corp_code 없음: stockCode={} → corpCode 동기화 필요", stockCode);
                return 0;
            }
            String corpCode = corpCodeOpt.get().getCorpCode();

            // 2. 공시 목록 API 호출
            DartDisclosureResponse response = disclosureClient.getDisclosureList(
                    corpCode,
                    fromDate.format(DART_DATE_FORMAT),
                    toDate.format(DART_DATE_FORMAT)
            );

            if (response.list() == null || response.list().isEmpty()) {
                success = true;
                return 0;
            }

            // 3. dart_disclosure 저장
            int savedCount = 0;
            for (DartDisclosureResponse.DisclosureItem item : response.list()) {
                LocalDate disclosureDate = parseDate(item.receiptDate());
                if (disclosureDate == null) continue;

                dartDisclosureMapper.insertIgnore(DartDisclosure.builder()
                        .corpCode(item.corpCode())
                        .stockCode(item.stockCode())
                        .corpName(item.corpName())
                        .receiptNo(item.receiptNo())
                        .reportName(item.reportName())
                        .disclosureDate(disclosureDate)
                        .submitter(item.submitter())
                        .disclosureUrl("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + item.receiptNo())
                        .isImportant(false)
                        .build());
                savedCount++;
            }

            success = true;
            log.info("[DART] 공시 저장 완료: stockCode={}, corpCode={}, count={}",
                    stockCode, corpCode, savedCount);
            return savedCount;

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[DART] 공시 수집 실패: stockCode={}, error={}", stockCode, errorMessage);
            throw e;
        } finally {
            saveApiLog("공시목록조회", "/api/list.json?stockCode=" + stockCode,
                    success, errorMessage, System.currentTimeMillis() - start);
        }
    }

    /**
     * 저장된 공시 조회
     */
    @Transactional(readOnly = true)
    public List<DartDisclosure> getDisclosures(String stockCode, LocalDate fromDate, LocalDate toDate) {
        return dartDisclosureMapper.findByStockCodeAndDateRange(stockCode, fromDate, toDate);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) return null;
        try {
            return LocalDate.parse(dateStr, DART_DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveApiLog(String apiName, String requestUrl, boolean success,
                             String errorMessage, long elapsedMs) {
        try {
            externalApiCallLogMapper.insert(ExternalApiCallLog.builder()
                    .provider("DART")
                    .apiName(apiName)
                    .httpMethod("GET")
                    .requestUrl(requestUrl)
                    .success(success)
                    .errorMessage(errorMessage)
                    .elapsedMs(elapsedMs)
                    .calledAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[ExternalApiLog] 로그 저장 실패: {}", e.getMessage());
        }
    }
}

