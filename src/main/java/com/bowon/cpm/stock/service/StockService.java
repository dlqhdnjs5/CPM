package com.bowon.cpm.stock.service;

import com.bowon.cpm.stock.domain.StockMaster;
import com.bowon.cpm.stock.mapper.StockMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private static final String UNKNOWN_MARKET_TYPE = "UNKNOWN";

    private final StockMasterMapper stockMasterMapper;

    /**
     * 종목 단건 조회
     */
    @Transactional(readOnly = true)
    public Optional<StockMaster> findByStockCode(String stockCode) {
        return stockMasterMapper.findByStockCode(stockCode);
    }

    /**
     * 활성 종목 전체 조회
     */
    @Transactional(readOnly = true)
    public List<StockMaster> findAllActive() {
        return stockMasterMapper.findAllActive();
    }

    /**
     * 종목 정보 저장/갱신 (없으면 INSERT, 있으면 UPDATE)
     *
     * @param stockCode  종목 코드
     * @param stockName  종목명
     * @param marketType 시장 구분 (KOSPI / KOSDAQ)
     */
    @Transactional
    public void upsertStockMaster(String stockCode, String stockName, String marketType) {
        StockMaster stockMaster = StockMaster.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .marketType(marketType)
                .isActive(true)
                .build();

        stockMasterMapper.upsert(stockMaster);
        log.debug("[Stock] stock_master upsert: stockCode={}, name={}", stockCode, stockName);
    }

    @Transactional
    public void upsertStockMasterFromDart(String stockCode, String stockName, String corpCode) {
        StockMaster stockMaster = StockMaster.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .marketType(UNKNOWN_MARKET_TYPE)
                .corpCode(corpCode)
                .isActive(true)
                .build();

        stockMasterMapper.upsert(stockMaster);
        log.debug("[Stock] stock_master DART upsert: stockCode={}, name={}, corpCode={}",
                stockCode, stockName, corpCode);
    }
}

