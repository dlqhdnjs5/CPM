package com.bowon.cpm.scheduler;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 일간 피드백 스케줄러
 * - 16:40 장 마감 후: 오늘 생성된 AI 판단의 성공/실패 평가
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyFeedbackScheduler {

    private static final String NAME = "DailyFeedbackScheduler";
    private final SchedulerLogSupport logSupport;
    private final FeedbackService feedbackService;
    private final AiDecisionMapper aiDecisionMapper;

    @Scheduled(cron = "0 40 16 * * MON-FRI")
    public void run() {
        if (!logSupport.isWeekday() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            // 오늘 생성된 판단 중 BUY/SELL (HOLD 제외)
            List<AiDecision> todayDecisions = aiDecisionMapper.findTodayDecisions();
            int count = 0;
            for (AiDecision decision : todayDecisions) {
                try {
                    feedbackService.evaluateDecision(decision.getId(), "DAILY");
                    count++;
                } catch (Exception e) {
                    log.warn("[{}] 피드백 실패 id={}: {}", NAME, decision.getId(), e.getMessage());
                }
            }
            logSupport.success(logId, "피드백 " + count + "건 생성");
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}

