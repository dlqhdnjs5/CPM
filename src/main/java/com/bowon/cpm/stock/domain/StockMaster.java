package com.bowon.cpm.stock.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 종목 기본 정보
 * 테이블: stock_master
 */
@Getter
@Builder
public class StockMaster {
    /** 종목 코드 (PK, 6자리) */
    private String stockCode;
    /** 종목명 */
    private String stockName;
    /** 시장 구분: KOSPI, KOSDAQ */
    private String marketType;
    /** 업종명 */
    private String sectorName;
    /** 산업명 */
    private String industryName;
    /** OpenDART corp_code (DART 연동 후 채움) */
    private String corpCode;
    /** 활성 여부 */
    private Boolean isActive;
    private Boolean isWatched;
    /** 상장일 */
    private LocalDate listedDate;
    /** 상폐일 */
    private LocalDate delistedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

