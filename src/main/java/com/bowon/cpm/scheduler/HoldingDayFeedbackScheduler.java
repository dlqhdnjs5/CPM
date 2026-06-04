package com.bowon.cpm.scheduler;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Plan 14 Phase 2: HoldingDay 만기 평가
 * - 매 거래일 장 마감 후 17:00
 * - expected_holding_days 만기가 도래한 AI 판단을 일괄 평가하여
 *   ai_feedback (evaluation_type='HOLDING_END') 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingDayFeedbackScheduler {

    private static final String NAME = "HoldingDayFeedbackScheduler";
    private final SchedulerLogSupport logSupport;
    private final FeedbackService feedbackService;
    private final AiDecisionMapper aiDecisionMapper;

    @Scheduled(cron = "0 0 17 * * MON-FRI")
    public void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            List<AiDecision> targets = aiDecisionMapper.findHoldingDayMaturedDecisions(LocalDate.now());
            int ok = 0, fail = 0;
            for (AiDecision d : targets) {
                try {
                    feedbackService.evaluateHoldingDayEnd(d.getId());
                    ok++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[{}] 평가 실패 id={}: {}", NAME, d.getId(), e.getMessage());
                }
            }
            logSupport.success(logId, "HOLDING_END 평가 ok=" + ok + " fail=" + fail);
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

