package com.bowon.cpm.risk.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 리스크 검증 결과
 * 테이블: risk_check_result
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskCheckResult {
    @Setter
    private Long id;
    private Long aiDecisionId;
    private String policyCode;
    private String accountNo;
    private String stockCode;
    /** true = 통과, false = 차단 */
    private Boolean passed;
    /** 차단 사유 (통과 시 null) */
    private String failReason;
    private BigDecimal availableCash;
    private BigDecimal expectedOrderAmount;
    private BigDecimal maxPositionAmount;
    private BigDecimal currentPositionAmount;
    private BigDecimal confidence;
    private BigDecimal riskRewardRatio;
    private LocalDateTime checkedAt;
}
