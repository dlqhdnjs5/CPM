package com.bowon.cpm.market.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MarketIndexMapper {

    Optional<BigDecimal> findLatestChangeRateByCodes(@Param("indexCodes") List<String> indexCodes);

    Optional<BigDecimal> calculateMarketTypeChangeRate(@Param("marketType") String marketType);

    Optional<BigDecimal> calculateSectorChangeRate(@Param("sectorName") String sectorName);
}
