package com.bowon.cpm.market.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MarketContext {
    private BigDecimal kospiChangeRate;
    private BigDecimal kosdaqChangeRate;
    private BigDecimal sectorChangeRate;
    private BigDecimal soxIndexChangeRate;
    private BigDecimal usdKrwChangeRate;
    private String sectorName;
    private String marketType;
}
