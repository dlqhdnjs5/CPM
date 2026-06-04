package com.bowon.cpm.paper.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaperPortfolioProfitLoss {
    private Long id;
    private String accountNo;
    private String stockCode;
    private LocalDate baseDate;
    private String evaluationType;
    private BigDecimal startAssetAmount;
    private BigDecimal endAssetAmount;
    private BigDecimal realizedProfitLoss;
    private BigDecimal unrealizedProfitLoss;
    private BigDecimal returnRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
