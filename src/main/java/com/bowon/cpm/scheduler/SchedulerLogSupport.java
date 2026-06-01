package com.bowon.cpm.scheduler;

import com.bowon.cpm.common.domain.SchedulerExecutionLog;
import com.bowon.cpm.common.mapper.SchedulerExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 스케줄러 공통 유틸
 * - 중복 실행 방지
 * - 실행 로그 기록
 * - 평일/장중 체크
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerLogSupport {

    private final SchedulerExecutionLogMapper logMapper;

    /** 평일 여부 */
    public boolean isWeekday() {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** 장중 여부 (09:00 ~ 15:30) */
    public boolean isMarketOpen() {
        LocalTime now = LocalTime.now();
        return isWeekday()
                && !now.isBefore(LocalTime.of(9, 0))
                && !now.isAfter(LocalTime.of(15, 30));
    }

    /** 이미 RUNNING 중이면 true (중복 실행 방지) */
    public boolean isAlreadyRunning(String schedulerName) {
        return logMapper.countRunning(schedulerName) > 0;
    }

    /** 실행 시작 기록 → ID 반환 */
    public Long start(String schedulerName) {
        SchedulerExecutionLog logEntry = SchedulerExecutionLog.builder()
                .schedulerName(schedulerName)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();
        logMapper.insert(logEntry);
        return logEntry.getId();
    }

    /** 성공 기록 */
    public void success(Long logId, String message) {
        logMapper.updateFinished(logId, "SUCCESS", message);
    }

    /** 실패 기록 */
    public void fail(Long logId, String message) {
        String msg = message != null && message.length() > 500 ? message.substring(0, 500) : message;
        logMapper.updateFinished(logId, "FAILED", msg);
    }
}

