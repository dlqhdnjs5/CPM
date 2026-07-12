package com.bowon.cpm.paper.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaperAccountBalance {
    private Long id;
    private String accountNo;
    private LocalDateTime baseDatetime;
    private BigDecimal cashBalance;
    private BigDecimal availableCash;
    private BigDecimal totalAssetAmount;
    private BigDecimal totalEvaluationAmount;
    private BigDecimal totalProfitLossAmount;
    private BigDecimal totalProfitLossRate;
    private LocalDateTime createdAt;
}
