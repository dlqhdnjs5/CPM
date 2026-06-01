package com.bowon.cpm.common.mapper;

import com.bowon.cpm.common.domain.SchedulerExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SchedulerExecutionLogMapper {

    void insert(SchedulerExecutionLog log);

    void updateFinished(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("executionMessage") String executionMessage);

    /** 현재 RUNNING 중인 동일 스케줄러가 있는지 확인 */
    int countRunning(@Param("schedulerName") String schedulerName);
}

