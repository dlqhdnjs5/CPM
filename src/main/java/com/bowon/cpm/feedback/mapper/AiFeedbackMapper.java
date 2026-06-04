package com.bowon.cpm.feedback.mapper;

import com.bowon.cpm.feedback.domain.AiFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AiFeedbackMapper {

    /** INSERT IGNORE — UK: ai_decision_id + evaluation_type */
    void insertIgnore(AiFeedback feedback);

    /** AI 판단 ID로 피드백 목록 조회 */
    List<AiFeedback> findByAiDecisionId(Long aiDecisionId);

    /** 종목코드 기준 최근 N건 피드백 조회 (다음 AI 판단 프롬프트 포함용) */
    List<AiFeedback> findRecentByStockCode(
            @Param("stockCode") String stockCode,
            @Param("limit") int limit
    );

    /** 종목코드 + 평가타입 필터로 최근 N건 (Plan 14 Phase 1: DAILY 제외용) */
    List<AiFeedback> findRecentByStockCodeAndTypes(
            @Param("stockCode") String stockCode,
            @Param("types") List<String> types,
            @Param("limit") int limit
    );

    /** 단건 조회 */
    Optional<AiFeedback> findByDecisionIdAndType(
            @Param("aiDecisionId") Long aiDecisionId,
            @Param("evaluationType") String evaluationType
    );

    /**
     * 기간 내 ai_feedback 집계 (WEEKLY/MONTHLY 통계용).
     * - HOLDING_END 평가만 대상으로 함
     * - 결과 Map 키:
     *   total, success_cnt, target_cnt, stop_loss_cnt,
     *   avg_return, max_return, min_return
     */
    java.util.Map<String, Object> aggregateStatsBetween(
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to
    );

    /** 기간 내 HOLDING_END 피드백 목록 (요약 텍스트 생성 + confidence 구간 통계용) */
    List<AiFeedback> findHoldingEndBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}

