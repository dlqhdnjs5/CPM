package com.bowon.cpm.feedback.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 테이블: ai_periodic_summary
 *
 * WEEKLY / MONTHLY 집계 요약 (사용자가 미리 생성한 테이블).
 *
 * 스키마:
 *   id            BIGINT PK
 *   summary_type  VARCHAR(20)   'WEEKLY' | 'MONTHLY'
 *   period_start  DATE
 *   period_end    DATE
 *   stats_json    JSON          집계 통계 원본(JSON 문자열로 저장)
 *   llm_summary   TEXT          OpenAI 자연어 요약
 *   created_at    DATETIME
 *
 * UK: (summary_type, period_start, period_end)
 */
@Getter
@Builder
public class AiPeriodicSummary {
    @Setter
    private Long id;
    /** WEEKLY / MONTHLY */
    private String summaryType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    /** 통계 원본 (JSON 문자열) */
    private String statsJson;
    /** OpenAI 자연어 요약 */
    private String llmSummary;
    private LocalDateTime createdAt;
}

