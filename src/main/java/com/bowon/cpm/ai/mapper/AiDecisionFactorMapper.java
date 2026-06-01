package com.bowon.cpm.ai.mapper;

import com.bowon.cpm.ai.domain.AiDecisionFactor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiDecisionFactorMapper {
    void insertBatch(@Param("list") List<AiDecisionFactor> list);
}

