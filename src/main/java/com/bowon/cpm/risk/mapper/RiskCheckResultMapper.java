package com.bowon.cpm.risk.mapper;

import com.bowon.cpm.risk.domain.RiskCheckResult;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskCheckResultMapper {
    void insert(RiskCheckResult result);
}

