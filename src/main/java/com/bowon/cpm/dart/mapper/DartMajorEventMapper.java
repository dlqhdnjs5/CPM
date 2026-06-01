package com.bowon.cpm.dart.mapper;

import com.bowon.cpm.dart.domain.DartMajorEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /** summary 업데이트 (OpenAI 요약 생성 후 반영) */
    void updateSummary(
            @Param("id") Long id,
            @Param("summary") String summary
    );
}

