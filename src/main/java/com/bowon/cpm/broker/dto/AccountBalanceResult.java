package com.bowon.cpm.broker.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AccountBalanceResult {
    private String accountNo;
    private BigDecimal cashBalance;
    private BigDecimal availableCash;
    private BigDecimal totalAssetAmount;
    private BigDecimal totalEvaluationAmount;
    private BigDecimal totalProfitLossAmount;
    private BigDecimal totalProfitLossRate;
}

