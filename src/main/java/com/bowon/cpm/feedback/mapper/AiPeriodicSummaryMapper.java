package com.bowon.cpm.feedback.mapper;

import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AiPeriodicSummaryMapper {

    /** INSERT IGNORE — UK: summary_type + period_start + period_end */
    void insertIgnore(AiPeriodicSummary summary);

    /** 가장 최근 요약 1건 조회 (WEEKLY/MONTHLY 각각) */
    Optional<AiPeriodicSummary> findLatestBySummaryType(@Param("summaryType") String summaryType);

    /** 기간 정확히 일치 조회 */
    Optional<AiPeriodicSummary> findByTypeAndPeriod(
            @Param("summaryType") String summaryType,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    /** 최근 N건 */
    List<AiPeriodicSummary> findRecent(
            @Param("summaryType") String summaryType,
            @Param("limit") int limit
    );
}

