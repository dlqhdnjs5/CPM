package com.bowon.cpm.common.mapper;

import com.bowon.cpm.common.domain.ExternalApiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExternalApiCallLogMapper {
    void insert(ExternalApiCallLog log);

    List<ExternalApiCallLog> findRecentFailures(@Param("limit") int limit);
}

