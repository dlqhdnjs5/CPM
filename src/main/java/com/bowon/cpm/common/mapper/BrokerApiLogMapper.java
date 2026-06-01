package com.bowon.cpm.common.mapper;

import com.bowon.cpm.common.domain.BrokerApiLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BrokerApiLogMapper {
    void insert(BrokerApiLog log);
}

