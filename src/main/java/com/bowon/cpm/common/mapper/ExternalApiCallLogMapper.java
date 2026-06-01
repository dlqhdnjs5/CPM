package com.bowon.cpm.common.mapper;

import com.bowon.cpm.common.domain.ExternalApiCallLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExternalApiCallLogMapper {
    void insert(ExternalApiCallLog log);
}

