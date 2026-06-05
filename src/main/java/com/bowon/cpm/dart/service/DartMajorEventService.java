package com.bowon.cpm.dart.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.dart.mapper.DartDisclosureMapper;
import com.bowon.cpm.dart.mapper.DartMajorEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * dart_disclosure report names are classified into investor-relevant events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartMajorEventService {

    private final DartDisclosureMapper disclosureMapper;
    private final DartMajorEventMapper majorEventMapper;
    private final OpenAiDecisionClient openAiClient;
    private final OpenAiProperties openAiProperties;

    private static final Map<String, String[]> EVENT_KEYWORDS = Map.ofEntries(
            Map.entry("유상증자", new String[]{"CAPITAL_INCREASE", "9.0"}),
            Map.entry("감자결정", new String[]{"CAPITAL_DECREASE", "8.5"}),
            Map.entry("현금배당", new String[]{"DIVIDEND", "6.0"}),
            Map.entry("주식배당", new String[]{"DIVIDEND", "6.5"}),
            Map.entry("단일판매", new String[]{"SINGLE_CONTRACT", "7.0"}),
            Map.entry("단일공급", new String[]{"SINGLE_CONTRACT", "7.0"}),
            Map.entry("최대주주변경", new String[]{"MAJOR_SHAREHOLDER", "8.0"}),
            Map.entry("주식등의대량보유상황보고서", new String[]{"MAJOR_SHAREHOLDER", "7.0"}),
            Map.entry("임원ㆍ주요주주", new String[]{"INSIDER_OWNERSHIP", "6.5"}),
            Map.entry("자기주식취득", new String[]{"TREASURY_STOCK", "6.0"}),
            Map.entry("합병", new String[]{"MERGER", "9.0"}),
            Map.entry("분할", new String[]{"SPIN_OFF", "8.5"}),
            Map.entry("대표이사변경", new String[]{"CEO_CHANGE", "7.5"}),
            Map.entry("타인에대한채무보증결정", new String[]{"DEBT_GUARANTEE", "7.0"}),
            Map.entry("영업(잠정)실적", new String[]{"EARNINGS_PREVIEW", "7.0"}),
            Map.entry("감사보고서제출", new String[]{"AUDIT_REPORT", "6.5"}),
            Map.entry("기업가치제고계획", new String[]{"VALUE_UP_PLAN", "6.5"})
    );

    @Transactional
    public int classifyAndSave(String stockCode) {
        LocalDate from = LocalDate.now().minusMonths(6);
        LocalDate to = LocalDate.now();
        List<DartDisclosure> disclosures = disclosureMapper.findByStockCodeAndDateRange(stockCode, from, to);

        if (disclosures.isEmpty()) {
            log.debug("[DartMajorEvent] disclosure not found: stockCode={}", stockCode);
            return 0;
        }

        int savedCount = 0;
        for (DartDisclosure disclosure : disclosures) {
            String reportName = disclosure.getReportName();
            if (reportName == null) {
                continue;
            }

            String[] typeAndScore = detectEventType(reportName);
            if (typeAndScore == null) {
                continue;
            }

            String eventType = typeAndScore[0];
            BigDecimal score = new BigDecimal(typeAndScore[1]);

            DartMajorEvent event = DartMajorEvent.builder()
                    .corpCode(disclosure.getCorpCode())
                    .stockCode(stockCode)
                    .receiptNo(disclosure.getReceiptNo())
                    .eventType(eventType)
                    .eventTitle(reportName)
                    .eventDate(disclosure.getDisclosureDate())
                    .importanceScore(score)
                    .summary(null)
                    .rawJson(null)
                    .build();

            majorEventMapper.insertIgnore(event);

            if (event.getId() != null) {
                String summary = generateSummary(reportName, eventType, stockCode);
                if (summary != null) {
                    majorEventMapper.updateSummary(event.getId(), summary);
                }
                savedCount++;
            }
        }

        log.info("[DartMajorEvent] major events saved: stockCode={}, count={}", stockCode, savedCount);
        return savedCount;
    }

    private String[] detectEventType(String reportName) {
        for (Map.Entry<String, String[]> entry : EVENT_KEYWORDS.entrySet()) {
            if (reportName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String generateSummary(String reportName, String eventType, String stockCode) {
        String prompt = String.format(
                "주식 공시 제목을 1줄로 요약하라.\n공시: %s\n이벤트 종류: %s\n종목: %s\n"
                        + "투자자 관점에서 핵심 내용과 주가 영향 가능성을 한 문장으로 작성하라.",
                reportName, eventType, stockCode
        );

        try {
            OpenAiResponse response = openAiClient.createTextCompletion(
                    "너는 주식 공시 분석 전문가다.",
                    prompt,
                    openAiProperties.modelSummary()
            );
            String text = response.extractText();
            return text != null ? text.trim() : null;
        } catch (Exception e) {
            log.warn("[DartMajorEvent] OpenAI summary failed: {}", e.getMessage());
            return reportName;
        }
    }

    @Transactional(readOnly = true)
    public List<DartMajorEvent> getMajorEvents(String stockCode, int limit) {
        return majorEventMapper.findByStockCode(stockCode, limit);
    }
}
