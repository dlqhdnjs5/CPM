package com.bowon.cpm.news.client.dto;

import java.util.List;

/**
 * 네이버 뉴스 검색 API 응답
 * API: GET /v1/search/news.json
 */
public record NaverNewsResponse(
        String lastBuildDate,
        int total,
        int start,
        int display,
        List<NewsItem> items
) {
    public record NewsItem(
            /** 뉴스 제목 (HTML 태그 포함 가능) */
            String title,

            /** 원본 뉴스 URL */
            String originallink,

            /** 네이버 뉴스 링크 */
            String link,

            /** 뉴스 요약 (HTML 태그 포함 가능) */
            String description,

            /** 발행일 (EEE, dd MMM yyyy HH:mm:ss +0900) */
            String pubDate
    ) {
    }
}

