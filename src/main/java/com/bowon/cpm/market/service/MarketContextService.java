package com.bowon.cpm.market.service;

import com.bowon.cpm.market.domain.MarketContext;
import com.bowon.cpm.market.mapper.MarketIndexMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketContextService {

    private static final List<String> KOSPI_CODES = List.of("KOSPI", "KS11", "0001");
    private static final List<String> KOSDAQ_CODES = List.of("KOSDAQ", "KQ11", "1001");
    private static final List<String> SOX_CODES = List.of("SOX", "PHLX_SOX", "NASDAQ_SOX");
    private static final List<String> USD_KRW_CODES = List.of("USD_KRW", "USDKRW", "USD/KRW");

    private final MarketIndexMapper marketIndexMapper;

    @Transactional(readOnly = true)
    public MarketContext latestContext(String marketType, String sectorName) {
        String normalizedMarketType = normalize(marketType);
        String normalizedSectorName = normalizeText(sectorName);

        BigDecimal kospi = latestIndexChangeRate(KOSPI_CODES);
        BigDecimal kosdaq = latestIndexChangeRate(KOSDAQ_CODES);
        BigDecimal sector = null;

        if (kospi == null) {
            kospi = calculateMarketTypeChangeRate("KOSPI");
        }
        if (kosdaq == null) {
            kosdaq = calculateMarketTypeChangeRate("KOSDAQ");
        }
        if (normalizedSectorName != null) {
            sector = marketIndexMapper.calculateSectorChangeRate(normalizedSectorName).orElse(null);
        }

        return MarketContext.builder()
                .kospiChangeRate(kospi)
                .kosdaqChangeRate(kosdaq)
                .sectorChangeRate(sector)
                .soxIndexChangeRate(latestIndexChangeRate(SOX_CODES))
                .usdKrwChangeRate(latestIndexChangeRate(USD_KRW_CODES))
                .marketType(normalizedMarketType)
                .sectorName(normalizedSectorName)
                .build();
    }

    private BigDecimal latestIndexChangeRate(List<String> indexCodes) {
        return marketIndexMapper.findLatestChangeRateByCodes(indexCodes).orElse(null);
    }

    private BigDecimal calculateMarketTypeChangeRate(String marketType) {
        return marketIndexMapper.calculateMarketTypeChangeRate(marketType).orElse(null);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
