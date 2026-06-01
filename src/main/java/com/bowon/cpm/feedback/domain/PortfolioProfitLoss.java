package com.bowon.cpm.feedback.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 테이블: portfolio_profit_loss */
@Getter
@Builder
public class PortfolioProfitLoss {
    private Long id;
    private String accountNo;
    /** null = 전체 포트폴리오 집계 */
    private String stockCode;
    private LocalDate baseDate;
    /** DAILY / WEEKLY / MONTHLY */
    private String evaluationType;
    private BigDecimal startAssetAmount;
    private BigDecimal endAssetAmount;
    private BigDecimal realizedProfitLoss;
    private BigDecimal unrealizedProfitLoss;
    /** 수익률 (%) */
    private BigDecimal returnRate;
    private LocalDateTime createdAt;
}

