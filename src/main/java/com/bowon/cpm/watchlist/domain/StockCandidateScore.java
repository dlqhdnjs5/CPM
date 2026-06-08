package com.bowon.cpm.watchlist.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCandidateScore {
    private Long id;
    private String stockCode;
    private String stockName;
    private String corpCode;
    private BigDecimal score;
    private BigDecimal liquidityScore;
    private BigDecimal technicalScore;
    private BigDecimal newsScore;
    private BigDecimal dartScore;
    private BigDecimal fundamentalScore;
    private BigDecimal riskScore;
    private String reason;
    private String candidateStatus;
    private LocalDate scoredDate;
    private LocalDateTime scoredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
