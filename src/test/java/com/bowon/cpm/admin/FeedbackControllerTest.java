package com.bowon.cpm.admin;

import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import com.bowon.cpm.feedback.service.FeedbackService;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.scheduler.HoldingDayFeedbackScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan 18 Case 15~16: FeedbackController 수동 트리거 API 테스트.
 * DB 미사용 — Service/Scheduler MockBean.
 */
@WebMvcTest(FeedbackController.class)
class FeedbackControllerTest {

    @Autowired MockMvc mvc;

    @MockBean FeedbackService feedbackService;
    @MockBean HoldingDayFeedbackScheduler holdingDayFeedbackScheduler;
    @MockBean TradingProperties tradingProperties;

    @Test
    @DisplayName("POST /api/feedback/holding-day/run → 200 + scheduler.run() 호출")
    void runHoldingDay_delegatesToScheduler() throws Exception {
        mvc.perform(post("/api/feedback/holding-day/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(holdingDayFeedbackScheduler, times(1)).run();
        verifyNoInteractions(feedbackService);
    }

    @Test
    @DisplayName("POST /api/feedback/weekly/run?from=&to= → evaluateWeekly 위임")
    void runWeekly_withDates_delegates() throws Exception {
        LocalDate start = LocalDate.of(2026, 5, 25);
        LocalDate end = LocalDate.of(2026, 5, 29);
        when(feedbackService.evaluateWeekly(eq(start), eq(end)))
                .thenReturn(AiPeriodicSummary.builder()
                        .summaryType("WEEKLY").periodStart(start).periodEnd(end)
                        .llmSummary("주간").build());

        mvc.perform(post("/api/feedback/weekly/run")
                        .param("from", "2026-05-25")
                        .param("to", "2026-05-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("주간"));

        verify(feedbackService, times(1)).evaluateWeekly(eq(start), eq(end));
    }

    @Test
    @DisplayName("POST /api/feedback/weekly/run (파라미터 없음) → 이번 주 자동 계산")
    void runWeekly_withoutDates_usesThisWeek() throws Exception {
        when(feedbackService.evaluateWeekly(any(), any()))
                .thenReturn(AiPeriodicSummary.builder().summaryType("WEEKLY").llmSummary("주간").build());

        mvc.perform(post("/api/feedback/weekly/run"))
                .andExpect(status().isOk());

        verify(feedbackService, times(1)).evaluateWeekly(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("POST /api/feedback/monthly/run?from=&to= → evaluateMonthly 위임")
    void runMonthly_withDates_delegates() throws Exception {
        LocalDate first = LocalDate.of(2026, 5, 1);
        LocalDate last = LocalDate.of(2026, 5, 31);
        when(feedbackService.evaluateMonthly(eq(first), eq(last)))
                .thenReturn(AiPeriodicSummary.builder().summaryType("MONTHLY")
                        .periodStart(first).periodEnd(last).llmSummary("월간").build());

        mvc.perform(post("/api/feedback/monthly/run")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("월간"));

        verify(feedbackService, times(1)).evaluateMonthly(eq(first), eq(last));
    }

    @Test
    @DisplayName("POST /api/feedback/monthly/run (파라미터 없음) → 전월 자동 계산")
    void runMonthly_withoutDates_usesLastMonth() throws Exception {
        when(feedbackService.evaluateMonthly(any(), any()))
                .thenReturn(AiPeriodicSummary.builder().summaryType("MONTHLY").llmSummary("월간").build());

        mvc.perform(post("/api/feedback/monthly/run"))
                .andExpect(status().isOk());

        verify(feedbackService, times(1)).evaluateMonthly(any(LocalDate.class), any(LocalDate.class));
    }
}

