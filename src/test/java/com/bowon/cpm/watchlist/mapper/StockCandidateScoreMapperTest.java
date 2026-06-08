package com.bowon.cpm.watchlist.mapper;

import com.bowon.cpm.support.TestProfiles;
import com.bowon.cpm.watchlist.domain.StockCandidateMetrics;
import com.bowon.cpm.watchlist.domain.StockCandidateScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ActiveProfiles(TestProfiles.TEST)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockCandidateScoreMapperTest {

    @Autowired StockCandidateScoreMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("candidate metrics query uses real DB rows and rolls back test DML")
    void findCandidateMetrics_realDbRollback() {
        LocalDate today = LocalDate.now();
        insertCandidate("999991", "99999991", "Candidate One", false, true);
        insertCandidate("999992", "99999992", "Watched Candidate", true, true);
        insertCandidate("999993", "99999993", "No Price Candidate", false, false);

        List<StockCandidateMetrics> prefetchTargets = mapper.findPrefetchTargets(10000);
        assertThat(prefetchTargets)
                .extracting(StockCandidateMetrics::getStockCode)
                .contains("999991", "999993")
                .doesNotContain("999992");

        List<StockCandidateMetrics> metrics = mapper.findCandidateMetrics(10000);
        assertThat(metrics)
                .extracting(StockCandidateMetrics::getStockCode)
                .contains("999991")
                .doesNotContain("999992", "999993");

        StockCandidateMetrics candidate = metrics.stream()
                .filter(item -> "999991".equals(item.getStockCode()))
                .findFirst()
                .orElseThrow();
        assertThat(candidate.getClosePrice()).isEqualByComparingTo("12000");
        assertThat(candidate.getPreviousClosePrice()).isEqualByComparingTo("11500");
        assertThat(candidate.getRecentNewsCount()).isEqualTo(1);
        assertThat(candidate.getRecentMajorEventCount()).isEqualTo(1);
        assertThat(candidate.getFinancialStatementCount()).isEqualTo(1);
        assertThat(candidate.getFundamentalTotalScore()).isEqualByComparingTo("14.5000");
        assertThat(candidate.getRoe()).isEqualByComparingTo("12.500000");
        assertThat(candidate.getPer()).isEqualByComparingTo("9.100000");

        mapper.upsert(StockCandidateScore.builder()
                .stockCode("999991")
                .stockName("Candidate One")
                .corpCode("99999991")
                .score(new BigDecimal("88.1234"))
                .liquidityScore(new BigDecimal("20.0000"))
                .technicalScore(new BigDecimal("25.0000"))
                .newsScore(new BigDecimal("18.0000"))
                .dartScore(new BigDecimal("10.0000"))
                .fundamentalScore(new BigDecimal("5.0000"))
                .riskScore(new BigDecimal("10.0000"))
                .reason("integration test")
                .candidateStatus("CANDIDATE")
                .scoredDate(today)
                .build());

        List<StockCandidateScore> scores = mapper.findLatestTop(today, 10);
        assertThat(scores)
                .extracting(StockCandidateScore::getStockCode)
                .contains("999991");

        mapper.updateStatus("999991", today, "AI_SELECTED");
        StockCandidateScore updated = mapper.findLatestTop(today, 10).stream()
                .filter(item -> "999991".equals(item.getStockCode()))
                .findFirst()
                .orElseThrow();
        assertThat(updated.getCandidateStatus()).isEqualTo("AI_SELECTED");
    }

    private void insertCandidate(String stockCode, String corpCode, String stockName,
                                 boolean watched, boolean withMarketData) {
        jdbcTemplate.update("""
                INSERT INTO dart_corp_code (corp_code, stock_code, corp_name, corp_eng_name, modify_date)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    stock_code = VALUES(stock_code),
                    corp_name = VALUES(corp_name),
                    corp_eng_name = VALUES(corp_eng_name),
                    modify_date = VALUES(modify_date)
                """, corpCode, stockCode, stockName, stockName + " ENG", LocalDate.now());

        jdbcTemplate.update("""
                INSERT INTO stock_master (stock_code, stock_name, market_type, corp_code, is_active, is_watched)
                VALUES (?, ?, 'UNKNOWN', ?, 1, ?)
                ON DUPLICATE KEY UPDATE
                    stock_name = VALUES(stock_name),
                    corp_code = VALUES(corp_code),
                    is_active = 1,
                    is_watched = VALUES(is_watched)
                """, stockCode, stockName, corpCode, watched);

        if (!withMarketData) {
            return;
        }

        LocalDate today = LocalDate.now();
        jdbcTemplate.update("""
                INSERT IGNORE INTO stock_price_daily (
                    stock_code, trade_date, open_price, high_price, low_price,
                    close_price, volume, trading_value, source
                ) VALUES (?, ?, 11000, 12300, 10900, 12000, 100000, 1200000000, 'TEST')
                """, stockCode, today);
        jdbcTemplate.update("""
                INSERT IGNORE INTO stock_price_daily (
                    stock_code, trade_date, open_price, high_price, low_price,
                    close_price, volume, trading_value, source
                ) VALUES (?, ?, 10800, 11600, 10700, 11500, 90000, 1035000000, 'TEST')
                """, stockCode, today.minusDays(1));
        jdbcTemplate.update("""
                INSERT INTO stock_indicator_daily (
                    stock_code, trade_date, ma5, ma20, ma60, rsi14,
                    volume_change_rate, volatility
                ) VALUES (?, ?, 11800, 11200, 10500, 61.5, 0.25, 0.04)
                ON DUPLICATE KEY UPDATE
                    ma5 = VALUES(ma5),
                    ma20 = VALUES(ma20),
                    ma60 = VALUES(ma60),
                    rsi14 = VALUES(rsi14),
                    volume_change_rate = VALUES(volume_change_rate),
                    volatility = VALUES(volatility)
                """, stockCode, today);

        String hash = "test-hash-" + stockCode;
        jdbcTemplate.update("""
                INSERT INTO stock_news (
                    stock_code, keyword, title, summary, origin_url,
                    origin_url_hash, naver_link, publisher, published_at, collected_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'TEST', NOW(), NOW())
                ON DUPLICATE KEY UPDATE stock_code = VALUES(stock_code)
                """, stockCode, stockName, "title " + stockCode, "summary",
                "https://example.com/" + stockCode, hash, "https://naver.example/" + stockCode);
        Long newsId = jdbcTemplate.queryForObject(
                "SELECT id FROM stock_news WHERE origin_url_hash = ?",
                Long.class,
                hash
        );
        jdbcTemplate.update("""
                INSERT IGNORE INTO news_sentiment (
                    news_id, stock_code, sentiment, sentiment_score, impact_score, reason, created_at
                ) VALUES (?, ?, 'POSITIVE', 0.7, 0.8, 'test', NOW())
                """, newsId, stockCode);

        jdbcTemplate.update("""
                INSERT IGNORE INTO dart_major_event (
                    corp_code, stock_code, receipt_no, event_type,
                    event_title, event_date, importance_score, summary, raw_json
                ) VALUES (?, ?, ?, 'OTHER', 'test event', ?, 0.5, 'summary', '{}')
                """, corpCode, stockCode, "R" + stockCode, today);
        jdbcTemplate.update("""
                INSERT IGNORE INTO dart_financial_statement (
                    corp_code, stock_code, business_year, report_code,
                    statement_type, account_id, account_name, amount, currency, raw_json
                ) VALUES (?, ?, ?, '11011', 'IS', 'ifrs-full_Revenue', 'Revenue', 1000000, 'KRW', '{}')
                """, corpCode, stockCode, today.getYear());
        jdbcTemplate.update("""
                INSERT INTO stock_fundamental_indicator (
                    stock_code, corp_code, business_year, report_code, base_date,
                    close_price, issued_shares, distributed_shares, market_cap,
                    revenue, operating_income, net_income, total_assets,
                    total_liabilities, total_equity, per, pbr, psr, roe, roa,
                    debt_ratio, operating_margin, net_margin, revenue_growth_rate,
                    profitability_score, stability_score, growth_score, valuation_score,
                    total_score, calculated_at
                ) VALUES (
                    ?, ?, ?, '11011', ?,
                    12000, 1000000, 900000, 12000000000,
                    1000000000, 120000000, 100000000, 2000000000,
                    800000000, 1200000000, 9.1, 1.2, 1.8, 12.5, 5.0,
                    66.6, 12.0, 10.0, 8.0,
                    5.0, 3.5, 2.0, 4.0,
                    14.5, NOW()
                )
                ON DUPLICATE KEY UPDATE
                    total_score = VALUES(total_score),
                    calculated_at = VALUES(calculated_at)
                """, stockCode, corpCode, today.getYear(), today);
    }
}
