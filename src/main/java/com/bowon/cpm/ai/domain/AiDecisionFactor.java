package com.bowon.cpm.ai.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 테이블: ai_decision_factor */
@Getter @Builder
public class AiDecisionFactor {
    @Setter
    private Long id;
    private Long aiDecisionId;
    /** TECHNICAL / NEWS / DART / FUNDAMENTAL / SUPPLY_DEMAND */
    private String factorType;
    /** POSITIVE / NEGATIVE / NEUTRAL */
    private String factorDirection;
    private BigDecimal factorScore;
    private String factorSummary;
    private LocalDateTime createdAt;
}
