package com.bowon.cpm.ai.mapper;

import com.bowon.cpm.ai.domain.AiDecisionRawResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiDecisionRawResponseMapper {
    void insert(AiDecisionRawResponse raw);

    /** 파싱 실패 시 parse_error 컬럼 업데이트 + is_parsed = 0 */
    void updateParseError(
            @Param("id") Long id,
            @Param("parseError") String parseError
    );

    /** 파싱 성공 시 is_parsed = 1, parse_error = NULL 로 정리 */
    void updateParsedSuccess(@Param("id") Long id);
}

