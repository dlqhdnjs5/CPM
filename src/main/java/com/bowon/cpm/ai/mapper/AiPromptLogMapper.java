package com.bowon.cpm.ai.mapper;

import com.bowon.cpm.ai.domain.AiPromptLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPromptLogMapper {
    void insert(AiPromptLog log);
}

