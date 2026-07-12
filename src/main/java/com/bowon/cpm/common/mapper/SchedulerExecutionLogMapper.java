package com.bowon.cpm.common.mapper;

import com.bowon.cpm.common.domain.SchedulerExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SchedulerExecutionLogMapper {

    void insert(SchedulerExecutionLog log);

    void updateFinished(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("executionMessage") String executionMessage);

    /** 현재 RUNNING 중인 동일 스케줄러가 있는지 확인 */
    int countRunning(@Param("schedulerName") String schedulerName);

    int expireStaleRunning(
            @Param("schedulerName") String schedulerName,
            @Param("timeoutBefore") LocalDateTime timeoutBefore,
            @Param("executionMessage") String executionMessage
    );

    List<SchedulerExecutionLog> findRecent(@Param("limit") int limit);
}

