package com.bowon.cpm.risk.domain;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

/**
 * 리스크 정책 설정
 * 테이블: risk_policy_config
 * 초기값은 DB에 DEFAULT_RISK_POLICY로 INSERT됨 (DDL 참조)
 */
@Getter
@Builder
public class RiskPolicyConfig {
    private Long id;
    private String policyCode;
    private String policyName;
    /** AI 최소 신뢰도 (예: 0.7) */
    private BigDecimal minConfidence;
    /** 종목별 최대 포지션 비중 (예: 0.2 = 20%) */
    private BigDecimal maxPositionWeight;
    /** 최대 주문 금액 (null = 무제한) */
    private BigDecimal maxOrderAmount;
    /** 최소 손익비 (예: 1.5) */
    private BigDecimal minRiskRewardRatio;
    /** 최대 허용 손실률 (예: -7.0 = -7%) */
    private BigDecimal maxExpectedLossRate;
    /** 당일 상승률 차단 임계치 (예: 15.0 = 15% 이상 상승 시 추격매수 차단) */
    private BigDecimal blockOverheatRate;
    private Boolean isActive;
}

