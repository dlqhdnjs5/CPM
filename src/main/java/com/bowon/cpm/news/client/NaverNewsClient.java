package com.bowon.cpm.news.client;

import com.bowon.cpm.news.client.dto.NaverNewsResponse;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 네이버 뉴스 검색 API 클라이언트
 *
 * API: GET /v1/search/news.json
 *
 * 인증 방식: HTTP Header
 * - X-Naver-Client-Id     : 네이버 개발자센터 Client ID
 * - X-Naver-Client-Secret : 네이버 개발자센터 Client Secret
 *
 * Query Parameter 설명:
 * - query   : 검색어 (종목명, 회사명 등)
 * - display : 한 번에 표시할 검색 결과 수 (최대 100)
 * - start   : 검색 시작 위치 (1~1000)
 * - sort    : 정렬 방식
 *             "date" = 최신순 (기본값으로 사용)
 *             "sim"  = 유사도순
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewsClient {

    private final WebClient naverWebClient;

    @Value("${external.naver.client-id}")
    private String clientId;

    @Value("${external.naver.client-secret}")
    private String clientSecret;

    /**
     * 뉴스 검색
     *
     * @param keyword 검색 키워드 (예: "삼성전자")
     * @param display 결과 수 (최대 100)
     * @param start   시작 위치 (1부터)
     */
    public NaverNewsResponse searchNews(String keyword, int display, int start) {
        log.debug("[Naver] 뉴스 검색: keyword={}, display={}, start={}", keyword, display, start);

        try {
            NaverNewsResponse response = naverWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/news.json")
                            .queryParam("query", keyword)   // 검색어
                            .queryParam("display", display) // 결과 수
                            .queryParam("start", start)     // 시작 위치
                            .queryParam("sort", "date")     // 최신순 정렬
                            .build())
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .bodyToMono(NaverNewsResponse.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("NAVER", "뉴스 검색 응답 없음: keyword=" + keyword);
            }

            return response;

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("NAVER", "뉴스 검색 중 오류: " + e.getMessage());
        }
    }
}

