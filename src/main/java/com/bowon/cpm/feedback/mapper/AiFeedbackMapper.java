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

    /** 단건 조회 */
    Optional<AiFeedback> findByDecisionIdAndType(
            @Param("aiDecisionId") Long aiDecisionId,
            @Param("evaluationType") String evaluationType
    );
}

