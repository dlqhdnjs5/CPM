package com.bowon.cpm.feedback.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 테이블: ai_feedback */
@Getter
@Builder
public class AiFeedback {
    @Setter
    private Long id;
    private Long aiDecisionId;
    private String stockCode;
    /** DAILY / WEEKLY / MONTHLY */
    private String evaluationType;
    /** 판단 당시 현재가 */
    private BigDecimal basePrice;
    /** 평가 시점 현재가 */
    private BigDecimal evaluatedPrice;
    private BigDecimal highestPrice;
    private BigDecimal lowestPrice;
    /** 수익률 (%) */
    private BigDecimal returnRate;
    /** 시장 수익률 (코스피 등) */
    private BigDecimal marketReturnRate;
    /** 초과 수익률 = returnRate - marketReturnRate */
    private BigDecimal excessReturnRate;
    /** 목표가 도달 여부 */
    private Boolean targetReached;
    /** 손절가 도달 여부 */
    private Boolean stopLossReached;
    /** 판단 성공 여부 */
    private Boolean success;
    /** 피드백 요약 (다음 AI 판단 프롬프트에 포함) */
    private String feedbackSummary;
    private LocalDateTime evaluatedAt;
}
