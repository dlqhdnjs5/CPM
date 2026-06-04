package com.bowon.cpm.ai.prompt;

import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 18 Case 17: AiDecisionPromptBuilder가
 * WEEKLY/MONTHLY 요약을 user prompt에 포함하는지 검증 (순수 단위).
 */
class AiDecisionPromptBuilderTest {

    private final AiDecisionPromptBuilder builder = new AiDecisionPromptBuilder();

    @Test
    @DisplayName("WEEKLY/MONTHLY 요약이 user prompt에 포함된다")
    void user_prompt_contains_weekly_and_monthly_summary() {
        AiPeriodicSummary weekly = AiPeriodicSummary.builder()
                .summaryType("WEEKLY")
                .periodStart(LocalDate.of(2026, 5, 25))
                .periodEnd(LocalDate.of(2026, 5, 29))
                .llmSummary("지난주 BUY 평균 수익률 +2.4%로 양호함")
                .build();
        AiPeriodicSummary monthly = AiPeriodicSummary.builder()
                .summaryType("MONTHLY")
                .periodStart(LocalDate.of(2026, 5, 1))
                .periodEnd(LocalDate.of(2026, 5, 31))
                .llmSummary("지난달 전략: confidence 0.85 이상 권고")
                .build();

        String prompt = builder.buildUserPrompt(
                "005930", "삼성전자",
                List.of(), List.of(), List.of(),
                new BigDecimal("10000000"), new BigDecimal("5000000"),
                List.of(), null, List.of(),
                null, null,
                weekly, monthly
        );

        assertThat(prompt).contains("지난주 BUY 평균 수익률 +2.4%로 양호함");
        assertThat(prompt).contains("지난달 전략: confidence 0.85 이상 권고");
        assertThat(prompt).contains("[참고: 지난주 WEEKLY 요약]");
        assertThat(prompt).contains("[참고: 지난달 MONTHLY 전략 개선 제안]");
        assertThat(prompt).contains("2026-05-25 ~ 2026-05-29");
        assertThat(prompt).contains("2026-05-01 ~ 2026-05-31");
    }

    @Test
    @DisplayName("WEEKLY/MONTHLY 요약이 null이면 '요약 없음'으로 표시")
    void user_prompt_fallback_when_summary_null() {
        String prompt = builder.buildUserPrompt(
                "005930", "삼성전자",
                List.of(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), null, List.of(),
                null, null,
                null, null
        );

        assertThat(prompt).contains("[참고: 지난주 WEEKLY 요약]");
        assertThat(prompt).contains("요약 없음 (아직 집계되지 않음)");
    }

    @Test
    @DisplayName("recentFeedbacks에 DAILY가 들어와도 그대로 표시되지만, 섹션 제목은 'DAILY 제외' 안내")
    void recent_feedbacks_section_has_daily_exclusion_label() {
        AiFeedback fb = AiFeedback.builder()
                .evaluationType("HOLDING_END")
                .feedbackSummary("[BUY 005930] 20일 보유 종료: 수익률 +5%")
                .build();

        String prompt = builder.buildUserPrompt(
                "005930", "삼성전자",
                List.of(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(fb), null, List.of(),
                null, null,
                null, null
        );

        assertThat(prompt).contains("## 과거 판단 피드백 (DAILY 제외)");
        assertThat(prompt).contains("[HOLDING_END]");
        assertThat(prompt).contains("수익률 +5%");
    }
}

