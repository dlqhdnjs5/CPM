package com.bowon.cpm.scheduler;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.feedback.service.FeedbackService;
import com.bowon.cpm.support.fixture.AiDecisionFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plan 17 Case 13: HoldingDayFeedbackScheduler 단위 테스트 (Mockito).
 * 실 DB 미사용 — FeedbackService는 mock으로만 호출 검증.
 */
@ExtendWith(MockitoExtension.class)
class HoldingDayFeedbackSchedulerTest {

    @Mock SchedulerLogSupport logSupport;
    @Mock FeedbackService feedbackService;
    @Mock AiDecisionMapper aiDecisionMapper;
    @InjectMocks HoldingDayFeedbackScheduler scheduler;

    @Test
    @DisplayName("Case 13: 만기 도래 판단을 조회해서 각각 FeedbackService에 위임한다")
    void run_delegates_each_matured_decision_to_feedback_service() {
        // Given
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(false);
        when(logSupport.start(anyString())).thenReturn(100L);

        AiDecision d1 = AiDecisionFixture.defaults().id(1L).build();
        AiDecision d2 = AiDecisionFixture.defaults().id(2L).build();
        when(aiDecisionMapper.findHoldingDayMaturedDecisions(any(LocalDate.class)))
                .thenReturn(List.of(d1, d2));

        // When
        scheduler.run();

        // Then
        verify(feedbackService, times(1)).evaluateHoldingDayEnd(1L);
        verify(feedbackService, times(1)).evaluateHoldingDayEnd(2L);
        verify(logSupport, times(1)).success(eq(100L), anyString());
    }

    @Test
    @DisplayName("주말이면 실행하지 않는다")
    void run_skips_on_weekend() {
        when(logSupport.isWeekday()).thenReturn(false);

        scheduler.run();

        verify(aiDecisionMapper, never()).findHoldingDayMaturedDecisions(any());
        verify(feedbackService, never()).evaluateHoldingDayEnd(any());
        verify(logSupport, never()).start(anyString());
    }

    @Test
    @DisplayName("이미 실행 중이면 중복 실행을 막는다")
    void run_skips_when_already_running() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(true);

        scheduler.run();

        verify(aiDecisionMapper, never()).findHoldingDayMaturedDecisions(any());
        verify(feedbackService, never()).evaluateHoldingDayEnd(any());
    }

    @Test
    @DisplayName("개별 평가가 실패해도 다음 건은 계속 처리한다")
    void run_continues_on_individual_failure() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(false);
        when(logSupport.start(anyString())).thenReturn(200L);

        AiDecision d1 = AiDecisionFixture.defaults().id(11L).build();
        AiDecision d2 = AiDecisionFixture.defaults().id(12L).build();
        when(aiDecisionMapper.findHoldingDayMaturedDecisions(any(LocalDate.class)))
                .thenReturn(List.of(d1, d2));
        when(feedbackService.evaluateHoldingDayEnd(11L)).thenThrow(new RuntimeException("일봉 없음"));

        scheduler.run();

        verify(feedbackService).evaluateHoldingDayEnd(11L);
        verify(feedbackService).evaluateHoldingDayEnd(12L); // 계속 진행
        verify(logSupport).success(eq(200L), anyString());
    }
}

