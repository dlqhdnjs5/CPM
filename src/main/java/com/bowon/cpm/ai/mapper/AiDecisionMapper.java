package com.bowon.cpm.ai.mapper;

import com.bowon.cpm.ai.domain.AiDecision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}

