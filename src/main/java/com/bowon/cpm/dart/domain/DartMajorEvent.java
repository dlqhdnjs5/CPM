package com.bowon.cpm.dart.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 테이블: dart_major_event
 * UK: receipt_no + event_type
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DartMajorEvent {
    @Setter
    private Long id;
    private String corpCode;
    private String stockCode;
    private String receiptNo;
    /**
     * CAPITAL_INCREASE, CAPITAL_DECREASE, DIVIDEND,
     * SINGLE_CONTRACT, MAJOR_SHAREHOLDER, OTHER
     */
    private String eventType;
    private String eventTitle;
    private LocalDate eventDate;
    private BigDecimal importanceScore;
    /** OpenAI 요약 or 정형 데이터 텍스트 요약 */
    private String summary;
    /** DART API 원문 응답 JSON */
    private String rawJson;
    private LocalDateTime createdAt;
}


