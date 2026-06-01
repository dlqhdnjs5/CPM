package com.bowon.cpm.ai.mapper;

import com.bowon.cpm.ai.domain.AiDecisionRawResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiDecisionRawResponseMapper {
    void insert(AiDecisionRawResponse raw);
}

