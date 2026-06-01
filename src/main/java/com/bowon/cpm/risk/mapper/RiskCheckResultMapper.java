package com.bowon.cpm.risk.mapper;

import com.bowon.cpm.risk.domain.RiskCheckResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface RiskCheckResultMapper {
    void insert(RiskCheckResult result);

    /** AI 판단 ID 기준 최신 리스크 검증 결과 조회 */
    Optional<RiskCheckResult> findLatestByAiDecisionId(@Param("aiDecisionId") Long aiDecisionId);
}

