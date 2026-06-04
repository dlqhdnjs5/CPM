package com.bowon.cpm.scheduler;

import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.order.service.OrderService;
import com.bowon.cpm.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 실행 스케줄러
 * - 장중 매 30분 (AI 판단 5분 후): 09:35, 10:05, ..., 15:05
 * - CREATED 상태인 BUY/SELL 판단 → 리스크 검증 → 통과 시 주문
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExecutionScheduler {

    private static final String NAME = "OrderExecutionScheduler";

    /**
     * 직전 사이클(약 30분) 이내 생성된 판단만 주문 대상으로 본다.
     * - AI 스케줄러는 30분마다 새 판단을 만들므로, 35분 이전 판단은 "낡은 판단"
     * - 35분 윈도우: 09:30 판단 → 09:35 처리 OK / 실패 시 10:00 판단 → 10:05 처리로 자연스럽게 갱신
     */
    private static final int FRESH_WINDOW_MINUTES = 35;
    /** 윈도우를 벗어난 CREATED 건은 EXPIRED로 정리 */
    private static final int EXPIRE_AFTER_MINUTES = 35;
    private final SchedulerLogSupport logSupport;
    private final AiDecisionMapper aiDecisionMapper;
    private final RiskService riskService;
    private final OrderService orderService;

    @Scheduled(cron = "0 35 9 * * MON-FRI")
    public void run0935() { run(); }

    @Scheduled(cron = "0 5 10-15 * * MON-FRI")
    public void runHourly() { run(); }

    @Scheduled(cron = "0 35 10-14 * * MON-FRI")
    public void runHalfHourly() { run(); }

    private void run() {
        if (!logSupport.isMarketOpen() || logSupport.isAlreadyRunning(NAME)) return;
        Long logId = logSupport.start(NAME);
        try {
            LocalDateTime now = LocalDateTime.now();

            // 1. 윈도우 벗어난 낡은 CREATED 판단은 EXPIRED 처리 (중복/추격 매수 방지)
            int expired = aiDecisionMapper.expireStaleDecisions(now.minusMinutes(EXPIRE_AFTER_MINUTES));

            // 2. 직전 사이클 이내 생성된 CREATED + BUY/SELL 판단 (종목별 최신 1건)
            List<AiDecision> pendingDecisions = aiDecisionMapper.findPendingDecisions(
                    "CREATED",
                    now.minusMinutes(FRESH_WINDOW_MINUTES)
            );
            int ordered = 0, riskFailed = 0, errorCount = 0;

            for (AiDecision decision : pendingDecisions) {
                try {
                    // 리스크 검증
                    var riskResult = riskService.checkAndSave(decision.getId());
                    if (!riskResult.getPassed()) {
                        riskFailed++;
                        continue;
                    }
                    // 주문 실행
                    orderService.placeOrder(decision.getId());
                    ordered++;
                } catch (Exception e) {
                    errorCount++;
                    log.warn("[{}] 주문 실패 aiDecisionId={}: {}", NAME, decision.getId(), e.getMessage());
                }
            }
            logSupport.success(logId,
                    "대상:" + pendingDecisions.size()
                            + ", 주문:" + ordered
                            + ", 리스크실패:" + riskFailed
                            + ", 에러:" + errorCount
                            + ", 만료:" + expired);
        } catch (Exception e) {
            log.error("[{}] 실패: {}", NAME, e.getMessage());
            logSupport.fail(logId, e.getMessage());
        }
    }
}





