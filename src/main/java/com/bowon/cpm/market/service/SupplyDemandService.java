package com.bowon.cpm.market.service;

import com.bowon.cpm.broker.kis.KisInvestorClient;
import com.bowon.cpm.broker.kis.dto.KisInvestorResponse;
import com.bowon.cpm.common.domain.BrokerApiLog;
import com.bowon.cpm.common.mapper.BrokerApiLogMapper;
import com.bowon.cpm.market.domain.StockSupplyDemandDaily;
import com.bowon.cpm.market.mapper.StockSupplyDemandDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyDemandService {

    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal KIS_INVESTOR_AMOUNT_UNIT = new BigDecimal("1000000");

    private final KisInvestorClient kisInvestorClient;
    private final StockSupplyDemandDailyMapper supplyDemandMapper;
    private final BrokerApiLogMapper brokerApiLogMapper;

    @Transactional
    public int fetchAndSave(String stockCode) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        int savedCount = 0;

        try {
            KisInvestorResponse response = kisInvestorClient.getInvestorFlow(stockCode);
            if (response.output() == null || response.output().isEmpty()) {
                log.warn("[SupplyDemand] no investor data: stockCode={}", stockCode);
                return 0;
            }

            List<StockSupplyDemandDaily> rows = response.output().stream()
                    .filter(output -> output.tradeDate() != null && !output.tradeDate().isBlank())
                    .map(output -> toDomain(stockCode, output))
                    .toList();

            if (!rows.isEmpty()) {
                supplyDemandMapper.insertBatch(rows);
                savedCount = rows.size();
            }

            success = true;
            log.info("[SupplyDemand] saved: stockCode={}, count={}", stockCode, savedCount);
            return savedCount;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("[SupplyDemand] failed: stockCode={}, error={}", stockCode, errorMessage);
            throw e;
        } finally {
            saveBrokerApiLog(stockCode, success, errorMessage, System.currentTimeMillis() - start);
        }
    }

    @Transactional(readOnly = true)
    public List<StockSupplyDemandDaily> getRecent(String stockCode, int limit) {
        return supplyDemandMapper.findByStockCode(stockCode, Math.max(1, limit));
    }

    @Transactional(readOnly = true)
    public StockSupplyDemandDaily getLatest(String stockCode) {
        return supplyDemandMapper.findLatestByStockCode(stockCode).orElse(null);
    }

    private StockSupplyDemandDaily toDomain(String stockCode, KisInvestorResponse.InvestorOutput output) {
        return StockSupplyDemandDaily.builder()
                .stockCode(stockCode)
                .tradeDate(LocalDate.parse(output.tradeDate(), KIS_DATE_FORMAT))
                .closePrice(parseBigDecimal(output.closePrice()))
                .individualNetBuyQty(parseLong(output.individualNetBuyQty()))
                .foreignNetBuyQty(parseLong(output.foreignNetBuyQty()))
                .institutionNetBuyQty(parseLong(output.institutionNetBuyQty()))
                .individualNetBuyAmount(parseKisInvestorAmount(output.individualNetBuyAmount()))
                .foreignNetBuyAmount(parseKisInvestorAmount(output.foreignNetBuyAmount()))
                .institutionNetBuyAmount(parseKisInvestorAmount(output.institutionNetBuyAmount()))
                .build();
    }

    private BigDecimal parseKisInvestorAmount(String value) {
        BigDecimal amount = parseBigDecimal(value);
        if (amount == null) {
            return null;
        }
        return amount.multiply(KIS_INVESTOR_AMOUNT_UNIT);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim().replace(",", ""));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim().replace(",", ""));
    }

    private void saveBrokerApiLog(String stockCode, boolean success, String errorMessage, long elapsedMs) {
        try {
            brokerApiLogMapper.insert(BrokerApiLog.builder()
                    .brokerType("KIS")
                    .apiName("투자자수급조회")
                    .httpMethod("GET")
                    .requestUrl("/uapi/domestic-stock/v1/quotations/inquire-investor?stockCode=" + stockCode)
                    .success(success)
                    .errorMessage(errorMessage)
                    .elapsedMs(elapsedMs)
                    .calledAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[BrokerApiLog] save failed: {}", e.getMessage());
        }
    }
}
