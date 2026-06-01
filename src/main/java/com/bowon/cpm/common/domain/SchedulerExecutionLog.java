package com.bowon.cpm.common.domain;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerExecutionLog {
    @Setter
    private Long id;
    private String schedulerName;
    private String status;          // RUNNING, SUCCESS, FAILED
    private String executionMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

