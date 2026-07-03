package com.bowon.cpm.dart.mapper;

import com.bowon.cpm.dart.domain.DartCorpCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DartCorpCodeMapper {

    /** 배치 저장 — 중복 시 UPDATE */
    void insertBatch(@Param("list") List<DartCorpCode> list);

    /** 종목코드로 corp_code 조회 */
    Optional<DartCorpCode> findByStockCode(String stockCode);

    /** corp_code로 조회 */
    Optional<DartCorpCode> findByCorpCode(String corpCode);
    List<DartCorpCode> searchInactiveListedCandidates(
            @Param("query") String query,
            @Param("limit") int limit
    );
}

