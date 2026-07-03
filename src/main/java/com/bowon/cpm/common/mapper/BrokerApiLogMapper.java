package com.bowon.cpm.common.mapper;

import com.bowon.cpm.common.domain.BrokerApiLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BrokerApiLogMapper {
    void insert(BrokerApiLog log);

    List<BrokerApiLog> findRecentFailures(@Param("limit") int limit);
}

