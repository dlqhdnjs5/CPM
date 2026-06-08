package com.bowon.cpm.watchlist.mapper;

import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.domain.StockCandidateScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockCandidateScoreMapper {

    List<StockCandidateMetrics> findCandidateMetrics(@Param("limit") int limit);

    List<StockCandidateMetrics> findPrefetchTargets(@Param("limit") int limit);

    void upsert(StockCandidateScore score);

    List<StockCandidateScore> findLatestTop(@Param("scoredDate") LocalDate scoredDate,
                                            @Param("limit") int limit);

    int updateStatus(@Param("stockCode") String stockCode,
                     @Param("scoredDate") LocalDate scoredDate,
                     @Param("candidateStatus") String candidateStatus);
}
