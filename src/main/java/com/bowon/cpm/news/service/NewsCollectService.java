package com.bowon.cpm.news.service;

import com.bowon.cpm.common.domain.ExternalApiCallLog;
import com.bowon.cpm.common.mapper.ExternalApiCallLogMapper;
import com.bowon.cpm.news.client.NaverNewsClient;
import com.bowon.cpm.news.client.dto.NaverNewsResponse;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.news.mapper.StockNewsMapper;
import com.bowon.cpm.news.util.NewsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectService {

    private final NaverNewsClient naverNewsClient;
    private final StockNewsMapper stockNewsMapper;
    private final ExternalApiCallLogMapper externalApiCallLogMapper;

    // 네이버 뉴스 pubDate 형식: "Sat, 31 May 2026 16:00:00 +0900"
    private static final DateTimeFormatter NAVER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    /**
     * 종목 키워드로 뉴스 수집 및 stock_news 저장
     * origin_url_hash UK로 중복 자동 제거 (INSERT IGNORE)
     *
     * @param stockCode 종목 코드
     * @param keyword   검색 키워드 (예: "삼성전자")
     * @param display   수집할 뉴스 수 (최대 100)
     * @return 저장 시도 건수
     */
    @Transactional
    public int collectNews(String stockCode, String keyword, int display) {
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            NaverNewsResponse response = naverNewsClient.searchNews(keyword, display, 1);

            if (response.items() == null || response.items().isEmpty()) {
                success = true;
                return 0;
            }

            int savedCount = 0;
            for (NaverNewsResponse.NewsItem item : response.items()) {
                // 원본 URL (없으면 네이버 링크로 대체)
                String originUrl = item.originallink() != null && !item.originallink().isBlank()
                        ? item.originallink()
                        : item.link();

                stockNewsMapper.insertIgnore(StockNews.builder()
                        .stockCode(stockCode)
                        .keyword(keyword)
                        .title(NewsUtils.cleanHtml(item.title()))
                        .summary(NewsUtils.cleanHtml(item.description()))
                        .originUrl(originUrl)
                        .originUrlHash(NewsUtils.sha256(originUrl))  // SHA-256 해시
                        .naverLink(item.link())
                        .publishedAt(parseNaverDate(item.pubDate()))
                        .collectedAt(LocalDateTime.now())
                        .build());
                savedCount++;
            }

            success = true;
            log.info("[News] 뉴스 수집 완료: stockCode={}, keyword={}, count={}",
                    stockCode, keyword, savedCount);
            return savedCount;

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("[News] 뉴스 수집 실패: keyword={}, error={}", keyword, errorMessage);
            throw e;
        } finally {
            saveApiLog(keyword, success, errorMessage, System.currentTimeMillis() - start);
        }
    }

    /**
     * 저장된 뉴스 조회
     */
    @Transactional(readOnly = true)
    public List<StockNews> getNews(String stockCode, int limit) {
        return stockNewsMapper.findByStockCode(stockCode, limit);
    }

    /**
     * 네이버 pubDate 파싱
     * 형식: "Sat, 31 May 2026 16:00:00 +0900"
     * 파싱 실패 시 null 반환 (허용)
     */
    private LocalDateTime parseNaverDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return null;
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(pubDate, NAVER_DATE_FORMAT);
            return zdt.toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.debug("[News] pubDate 파싱 실패: {}", pubDate);
            return null;
        }
    }

    private void saveApiLog(String keyword, boolean success, String errorMessage, long elapsedMs) {
        try {
            externalApiCallLogMapper.insert(ExternalApiCallLog.builder()
                    .provider("NAVER")
                    .apiName("뉴스검색")
                    .httpMethod("GET")
                    .requestUrl("/v1/search/news.json?query=" + keyword)
                    .success(success)
                    .errorMessage(errorMessage)
                    .elapsedMs(elapsedMs)
                    .calledAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[ExternalApiLog] 로그 저장 실패: {}", e.getMessage());
        }
    }
}

