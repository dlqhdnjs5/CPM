package com.bowon.cpm.dart.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table: dart_stock_quantity
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DartStockQuantity {
    private Long id;
    private String corpCode;
    private String stockCode;
    private Integer businessYear;
    private String reportCode;
    private String stockType;
    private Long issuedStockQuantity;
    private Long treasuryStockQuantity;
    private Long distributedStockQuantity;
    private LocalDate settlementDate;
    private String rawJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
