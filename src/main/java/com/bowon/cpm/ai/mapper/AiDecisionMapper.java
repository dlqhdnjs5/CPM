package com.bowon.cpm.ai.mapper;

import com.bowon.cpm.ai.domain.AiDecision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AiDecisionMapper {
    void insert(AiDecision decision);

    Optional<AiDecision> findById(Long id);

    List<AiDecision> findByStockCode(
            @Param("stockCode") String stockCode,
            @Param("limit") int limit
    );

    /** decision_status 업데이트 (재검토 결과 반영 등) */
    void updateDecisionStatus(
            @Param("id") Long id,
            @Param("decisionStatus") String decisionStatus
    );

    /** decision_status 기준 조회 (스케줄러: CREATED 상태 건 처리) */
    List<AiDecision> findByDecisionStatus(@Param("decisionStatus") String decisionStatus);

    /**
     * 주문 실행 대상 조회.
     * - decision_status = #{decisionStatus} (보통 'CREATED')
     * - decision = 'BUY'
     * - created_at >= #{since} (직전 사이클 내에 생성된 최신 판단만)
     * - 같은 stock_code에 여러 건이 있으면 가장 최신 1건만 반환
     */
    List<AiDecision> findPendingBuyDecisions(
            @Param("decisionStatus") String decisionStatus,
            @Param("since") LocalDateTime since
    );

    /**
     * 오래된 CREATED 판단을 EXPIRED로 일괄 만료 처리.
     * - decision_status = 'CREATED'
     * - created_at < #{before}
     */
    int expireStaleDecisions(@Param("before") LocalDateTime before);

    /** 오늘 생성된 BUY/SELL 판단 조회 (피드백 대상) */
    List<AiDecision> findTodayDecisions();

    /**
     * 보유 기간(expected_holding_days) 만기가 도래한 BUY/SELL 판단 중
     * 아직 HOLDING_END 피드백이 없는 건을 조회.
     *
     * 조건:
     *  - decision IN ('BUY','SELL')
     *  - expected_holding_days IS NOT NULL
     *  - DATE(created_at) + INTERVAL expected_holding_days DAY <= #{today}
     *  - LEFT JOIN ai_feedback WHERE evaluation_type='HOLDING_END' AND id IS NULL
     */
    List<AiDecision> findHoldingDayMaturedDecisions(@Param("today") java.time.LocalDate today);

    /** 기간 내 생성된 BUY/SELL 판단 조회 (WEEKLY/MONTHLY 통계용) */
    List<AiDecision> findDecisionsBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}

