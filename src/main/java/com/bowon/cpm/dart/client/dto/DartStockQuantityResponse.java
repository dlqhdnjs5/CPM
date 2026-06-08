package com.bowon.cpm.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenDART stock total quantity response.
 * API: /api/stockTotqySttus.json
 */
public record DartStockQuantityResponse(
        String status,
        String message,
        List<StockQuantityItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public boolean isEmpty() {
        return "100".equals(status);
    }

    public record StockQuantityItem(
            @JsonProperty("rcept_no") String receiptNo,
            @JsonProperty("corp_cls") String corpClass,
            @JsonProperty("corp_code") String corpCode,
            @JsonProperty("corp_name") String corpName,
            @JsonProperty("se") String stockType,
            @JsonProperty("isu_stock_totqy") String issuableStockTotalQuantity,
            @JsonProperty("now_to_isu_stock_totqy") String issuedUntilNowStockTotalQuantity,
            @JsonProperty("now_to_dcrs_stock_totqy") String decreasedUntilNowStockTotalQuantity,
            @JsonProperty("redc") String capitalReductionQuantity,
            @JsonProperty("profit_incnr") String profitCancellationQuantity,
            @JsonProperty("rdmstk_repy") String redeemedStockRepaymentQuantity,
            @JsonProperty("etc") String otherDecreaseQuantity,
            @JsonProperty("istc_totqy") String issuedStockQuantity,
            @JsonProperty("tesstk_co") String treasuryStockQuantity,
            @JsonProperty("distb_stock_co") String distributedStockQuantity,
            @JsonProperty("stlm_dt") String settlementDate
    ) {
    }
}
