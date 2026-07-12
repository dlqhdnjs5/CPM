package com.bowon.cpm.news.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.news.domain.NewsAiSummary;
import com.bowon.cpm.news.domain.NewsSentiment;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.news.mapper.NewsAiSummaryMapper;
import com.bowon.cpm.news.mapper.NewsSentimentMapper;
import com.bowon.cpm.news.mapper.StockNewsMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsAnalysisService {

    private final StockNewsMapper stockNewsMapper;
    private final NewsAiSummaryMapper newsAiSummaryMapper;
    private final NewsSentimentMapper newsSentimentMapper;
    private final OpenAiDecisionClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public AnalysisBatchResult analyzePending(int limit) {
        List<StockNews> targets = stockNewsMapper.findPendingAnalysisTargets(limit);
        int saved = 0;
        int failed = 0;
        for (StockNews news : targets) {
            try {
                analyzeOne(news);
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[NewsAnalysis] skipped newsId={}, error={}", news.getId(), e.getMessage());
            }
        }
        log.info("[NewsAnalysis] completed: requested={}, saved={}, failed={}", targets.size(), saved, failed);
        return new AnalysisBatchResult(targets.size(), saved, failed);
    }

    public void analyzeOne(StockNews news) throws Exception {
        if (news == null || news.getId() == null) {
            throw new IllegalArgumentException("news id is required");
        }

        OpenAiResponse response = openAiClient.createTextCompletion(
                buildSystemPrompt(),
                buildUserPrompt(news),
                openAiProperties.modelSummary()
        );
        String text = response.extractText();
        NewsAnalysisJson parsed = parseAnalysis(text);

        String keyPointsJson = objectMapper.writeValueAsString(
                parsed.keyPoints() != null ? parsed.keyPoints() : List.of()
        );
        OpenAiResponse.Usage usage = response.usage();

        newsAiSummaryMapper.insertIgnore(NewsAiSummary.builder()
                .newsId(news.getId())
                .stockCode(news.getStockCode())
                .summary(requiredText(parsed.summary(), "summary"))
                .keyPoints(keyPointsJson)
                .modelName(openAiProperties.modelSummary())
                .promptTokens(usage != null ? usage.inputTokens() : null)
                .completionTokens(usage != null ? usage.outputTokens() : null)
                .build());

        newsSentimentMapper.insertIgnore(NewsSentiment.builder()
                .newsId(news.getId())
                .stockCode(news.getStockCode())
                .sentiment(normalizeSentiment(parsed.sentiment()))
                .sentimentScore(clamp(parsed.sentimentScore(), "-1.0000", "1.0000"))
                .impactScore(clamp(parsed.impactScore(), "0.0000", "1.0000"))
                .reason(parsed.reason())
                .build());
    }

    NewsAnalysisJson parseAnalysis(String text) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty OpenAI response");
        }
        String json = extractJsonObject(text);
        return objectMapper.readValue(json, NewsAnalysisJson.class);
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("JSON object not found");
        }
        return text.substring(start, end + 1);
    }

    private String buildSystemPrompt() {
        return """
                You analyze Korean stock market news for an automated trading system.
                Return only a valid JSON object with these fields:
                summary: Korean concise summary within 2 sentences.
                keyPoints: array of 1 to 5 Korean key points.
                sentiment: POSITIVE, NEUTRAL, or NEGATIVE.
                sentimentScore: number from -1.0 to 1.0.
                impactScore: number from 0.0 to 1.0.
                reason: Korean explanation for sentiment and expected stock impact.
                Do not include markdown or code fences.
                """;
    }

    private String buildUserPrompt(StockNews news) {
        return """
                Analyze this news item.

                stockCode: %s
                keyword: %s
                title: %s
                naverSummary: %s
                publisher: %s
                publishedAt: %s
                url: %s
                """.formatted(
                nullToBlank(news.getStockCode()),
                nullToBlank(news.getKeyword()),
                nullToBlank(news.getTitle()),
                nullToBlank(news.getSummary()),
                nullToBlank(news.getPublisher()),
                news.getPublishedAt() != null ? news.getPublishedAt() : "",
                nullToBlank(news.getOriginUrl())
        );
    }

    private String normalizeSentiment(String sentiment) {
        if (sentiment == null) {
            return "NEUTRAL";
        }
        String normalized = sentiment.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POSITIVE", "NEGATIVE", "NEUTRAL" -> normalized;
            default -> "NEUTRAL";
        };
    }

    private BigDecimal clamp(BigDecimal value, String min, String max) {
        if (value == null) {
            return null;
        }
        BigDecimal lower = new BigDecimal(min);
        BigDecimal upper = new BigDecimal(max);
        BigDecimal clamped = value.max(lower).min(upper);
        return clamped.setScale(4, RoundingMode.HALF_UP);
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String nullToBlank(Object value) {
        return value != null ? value.toString() : "";
    }

    public record AnalysisBatchResult(int requested, int saved, int failed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewsAnalysisJson(
            String summary,
            List<String> keyPoints,
            String sentiment,
            BigDecimal sentimentScore,
            BigDecimal impactScore,
            String reason
    ) {}
}
