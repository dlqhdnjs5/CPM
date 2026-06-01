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
 * DART 주요 이벤트 분류 서비스
 *
 * dart_disclosure에서 보고서 제목을 분석해
 * 주요 이벤트를 dart_major_event에 저장하고 OpenAI로 요약한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartMajorEventService {

    private final DartDisclosureMapper disclosureMapper;
    private final DartMajorEventMapper majorEventMapper;
    private final OpenAiDecisionClient openAiClient;
    private final OpenAiProperties openAiProperties;

    /**
     * 키워드 → event_type + 중요도 점수 매핑
     */
    private static final Map<String, String[]> EVENT_KEYWORDS = Map.of(
            "유상증자",          new String[]{"CAPITAL_INCREASE", "9.0"},
            "감자결정",          new String[]{"CAPITAL_DECREASE", "8.5"},
            "현금배당",          new String[]{"DIVIDEND", "6.0"},
            "주식배당",          new String[]{"DIVIDEND", "6.5"},
            "단일판매",          new String[]{"SINGLE_CONTRACT", "7.0"},
            "단일공급",          new String[]{"SINGLE_CONTRACT", "7.0"},
            "최대주주변경",      new String[]{"MAJOR_SHAREHOLDER", "8.0"},
            "자기주식취득",      new String[]{"TREASURY_STOCK", "6.0"},
            "합병",              new String[]{"MERGER", "9.0"},
            "분할",              new String[]{"SPIN_OFF", "8.5"}
    );

    /**
     * 종목의 공시 목록에서 주요 이벤트 분류 + 저장 + OpenAI 요약
     *
     * @return 저장된 주요 이벤트 건수
     */
    @Transactional
    public int classifyAndSave(String stockCode) {
        // 최근 6개월 공시 조회
        LocalDate from = LocalDate.now().minusMonths(6);
        LocalDate to = LocalDate.now();
        List<DartDisclosure> disclosures = disclosureMapper.findByStockCodeAndDateRange(stockCode, from, to);

        if (disclosures.isEmpty()) {
            log.debug("[DartMajorEvent] 공시 없음: stockCode={}", stockCode);
            return 0;
        }

        int savedCount = 0;
        for (DartDisclosure disclosure : disclosures) {
            String reportName = disclosure.getReportName();
            if (reportName == null) continue;

            String[] typeAndScore = detectEventType(reportName);
            if (typeAndScore == null) continue;

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
                    .summary(null) // OpenAI 요약 후 업데이트
                    .rawJson(null)
                    .build();

            majorEventMapper.insertIgnore(event);

            // OpenAI로 요약 생성 (비용 절감: 간단한 텍스트 요약)
            if (event.getId() != null) {
                String summary = generateSummary(reportName, eventType, stockCode);
                if (summary != null) {
                    majorEventMapper.updateSummary(event.getId(), summary);
                }
                savedCount++;
            }
        }

        log.info("[DartMajorEvent] 주요 이벤트 저장: stockCode={}, {}건", stockCode, savedCount);
        return savedCount;
    }

    /**
     * report_name 키워드로 event_type 탐지
     */
    private String[] detectEventType(String reportName) {
        for (Map.Entry<String, String[]> entry : EVENT_KEYWORDS.entrySet()) {
            if (reportName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * OpenAI로 공시 이벤트 요약 생성
     */
    private String generateSummary(String reportName, String eventType, String stockCode) {
        String prompt = String.format(
                "주식 공시 제목을 1줄로 요약하라.\n공시: %s\n이벤트 종류: %s\n종목: %s\n" +
                "투자자 관점에서 핵심 내용과 주가 영향을 한 문장으로.",
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
            log.warn("[DartMajorEvent] OpenAI 요약 실패: {}", e.getMessage());
            // fallback: 공시 제목 그대로
            return reportName;
        }
    }

    /**
     * 저장된 주요 이벤트 조회
     */
    @Transactional(readOnly = true)
    public List<DartMajorEvent> getMajorEvents(String stockCode, int limit) {
        return majorEventMapper.findByStockCode(stockCode, limit);
    }
}

