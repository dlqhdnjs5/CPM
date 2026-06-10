package com.bowon.cpm.macro.service;

import com.bowon.cpm.news.service.NewsAnalysisService;
import com.bowon.cpm.news.service.NewsCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MacroNewsService {

    public static final String FED_CODE = "MACRO_FED";
    public static final String BOK_CODE = "MACRO_BOK";
    public static final String MARKET_CODE = "MACRO_MARKET";

    private static final Map<String, List<String>> DEFAULT_KEYWORDS = Map.of(
            FED_CODE, List.of(
                    "연준 금리",
                    "FOMC",
                    "Federal Reserve interest rate",
                    "Fed Chair speech"
            ),
            BOK_CODE, List.of(
                    "한국은행 기준금리",
                    "한국은행 금통위",
                    "한국은행 총재",
                    "한미 금리차"
            ),
            MARKET_CODE, List.of(
                    "원달러 환율",
                    "미국 10년물 금리",
                    "나스닥 반도체",
                    "코스피 외국인 수급"
            )
    );

    private final NewsCollectService newsCollectService;
    private final NewsAnalysisService newsAnalysisService;

    public MacroCollectResult collectDefault(int displayPerKeyword, boolean analyze, int analyzeLimit) {
        Map<String, Integer> savedByCode = new LinkedHashMap<>();
        int totalSaved = 0;

        for (Map.Entry<String, List<String>> entry : DEFAULT_KEYWORDS.entrySet()) {
            int savedForCode = 0;
            for (String keyword : entry.getValue()) {
                savedForCode += newsCollectService.collectNews(entry.getKey(), keyword, displayPerKeyword);
            }
            savedByCode.put(entry.getKey(), savedForCode);
            totalSaved += savedForCode;
        }

        NewsAnalysisService.AnalysisBatchResult analysis = analyze
                ? newsAnalysisService.analyzePending(analyzeLimit)
                : new NewsAnalysisService.AnalysisBatchResult(0, 0, 0);

        return new MacroCollectResult(totalSaved, savedByCode, analysis);
    }

    public record MacroCollectResult(
            int totalSaved,
            Map<String, Integer> savedByCode,
            NewsAnalysisService.AnalysisBatchResult analysis
    ) {}
}
