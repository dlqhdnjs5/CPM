package com.bowon.cpm.strategy.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StrategyConfig {
    @Setter
    private Long id;
    private String strategyCode;
    private String strategyName;
    private String strategyType;
    private String configJson;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
