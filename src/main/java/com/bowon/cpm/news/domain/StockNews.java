package com.bowon.cpm.news.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 종목 뉴스
 * 테이블: stock_news
 * 중복 제거: origin_url_hash (SHA-256 UK)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockNews {
    private Long id;
    private String stockCode;
    private String keyword;
    private String title;
    private String summary;
    private String originUrl;
    /** SHA-256(originUrl) — UK, 중복 제거용 */
    private String originUrlHash;
    private String naverLink;
    private String publisher;
    private LocalDateTime publishedAt;
    private LocalDateTime collectedAt;

    private String aiSummary;
    private String sentiment;
    private BigDecimal sentimentScore;
    private BigDecimal impactScore;
}

