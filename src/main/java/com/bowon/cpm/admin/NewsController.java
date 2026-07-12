package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.news.service.NewsAnalysisService;
import com.bowon.cpm.news.service.NewsCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NewsController {

    private final NewsCollectService newsCollectService;
    private final NewsAnalysisService newsAnalysisService;

    /**
     * 종목 뉴스 수집
     * POST /api/news/{stockCode}/fetch?keyword=삼성전자&display=30
     */
    @PostMapping("/news/{stockCode}/fetch")
    public ApiResponse<Map<String, Object>> fetchNews(
            @PathVariable String stockCode,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") int display
    ) {
        int count = newsCollectService.collectNews(stockCode, keyword, display);
        return ApiResponse.ok("뉴스 수집 완료", Map.of(
                "stockCode", stockCode,
                "keyword", keyword,
                "savedCount", count
        ));
    }

    /**
     * 저장된 뉴스 조회
     * GET /api/stocks/{stockCode}/news?limit=20
     */
    @GetMapping("/stocks/{stockCode}/news")
    public ApiResponse<List<StockNews>> getNews(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(newsCollectService.getNews(stockCode, limit));
    }

    @PostMapping("/news/analyze")
    public ApiResponse<Map<String, Object>> analyzeNews(
            @RequestParam(defaultValue = "50") int limit
    ) {
        NewsAnalysisService.AnalysisBatchResult result = newsAnalysisService.analyzePending(limit);
        return ApiResponse.ok("뉴스 요약/감성 분석 완료", Map.of(
                "requested", result.requested(),
                "saved", result.saved(),
                "failed", result.failed()
        ));
    }
}

