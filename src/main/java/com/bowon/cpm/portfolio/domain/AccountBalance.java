package com.bowon.cpm.portfolio.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class AccountBalance {
    private Long id;
    private String accountNo;
    private String brokerType;
    private LocalDateTime baseDatetime;
    private BigDecimal cashBalance;
    private BigDecimal availableCash;
    private BigDecimal totalAssetAmount;
    private BigDecimal totalEvaluationAmount;
    private BigDecimal totalProfitLossAmount;
    private BigDecimal totalProfitLossRate;
}

