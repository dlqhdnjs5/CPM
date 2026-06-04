package com.bowon.cpm.dart.mapper;

import com.bowon.cpm.dart.domain.DartMajorEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DartMajorEventMapper {

    /** INSERT IGNORE — UK: receipt_no + event_type */
    void insertIgnore(DartMajorEvent event);

    /** 종목코드 기준 최근 N건 조회 */
    List<DartMajorEvent> findByStockCode(
            @Param("stockCode") String stockCode,
            @Param("limit") int limit
    );

    /** 종목 + event_date since 이후 조회 (AI 판단용) */
    List<DartMajorEvent> findByStockCodeAndDateAfter(
            @Param("stockCode") String stockCode,
            @Param("since") LocalDate since,
            @Param("limit") int limit
    );

    /** summary 업데이트 (OpenAI 요약 생성 후 반영) */
    void updateSummary(
            @Param("id") Long id,
            @Param("summary") String summary
    );
}

