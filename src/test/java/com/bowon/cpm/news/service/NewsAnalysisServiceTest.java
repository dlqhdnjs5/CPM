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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsAnalysisServiceTest {

    @Mock StockNewsMapper stockNewsMapper;
    @Mock NewsAiSummaryMapper newsAiSummaryMapper;
    @Mock NewsSentimentMapper newsSentimentMapper;
    @Mock OpenAiDecisionClient openAiClient;
    @Mock OpenAiProperties openAiProperties;

    NewsAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new NewsAnalysisService(
                stockNewsMapper,
                newsAiSummaryMapper,
                newsSentimentMapper,
                openAiClient,
                openAiProperties,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Analyzes one news item and stores summary and sentiment")
    void analyzeOneStoresSummaryAndSentiment() throws Exception {
        when(openAiProperties.modelSummary()).thenReturn("gpt-4.1-mini");
        when(openAiClient.createTextCompletion(anyString(), anyString(), anyString()))
                .thenReturn(response("""
                        {
                          "summary": "실적 개선 기대가 부각됐다.",
                          "keyPoints": ["수주 증가", "이익률 개선"],
                          "sentiment": "POSITIVE",
                          "sentimentScore": 0.82,
                          "impactScore": 0.64,
                          "reason": "주가에 긍정적 재료다."
                        }
                        """));

        service.analyzeOne(news());

        ArgumentCaptor<NewsAiSummary> summaryCaptor = ArgumentCaptor.forClass(NewsAiSummary.class);
        ArgumentCaptor<NewsSentiment> sentimentCaptor = ArgumentCaptor.forClass(NewsSentiment.class);
        verify(newsAiSummaryMapper).insertIgnore(summaryCaptor.capture());
        verify(newsSentimentMapper).insertIgnore(sentimentCaptor.capture());

        assertThat(summaryCaptor.getValue().getSummary()).isEqualTo("실적 개선 기대가 부각됐다.");
        assertThat(summaryCaptor.getValue().getKeyPoints()).contains("수주 증가");
        assertThat(sentimentCaptor.getValue().getSentiment()).isEqualTo("POSITIVE");
        assertThat(sentimentCaptor.getValue().getSentimentScore()).isEqualByComparingTo(new BigDecimal("0.8200"));
        assertThat(sentimentCaptor.getValue().getImpactScore()).isEqualByComparingTo(new BigDecimal("0.6400"));
    }

    @Test
    @DisplayName("Batch continues when one news analysis fails")
    void batchContinuesOnFailure() {
        when(stockNewsMapper.findPendingAnalysisTargets(2)).thenReturn(List.of(news(), news(2L)));
        when(openAiProperties.modelSummary()).thenReturn("gpt-4.1-mini");
        when(openAiClient.createTextCompletion(anyString(), anyString(), anyString()))
                .thenReturn(response("{\"summary\":\"ok\",\"keyPoints\":[],\"sentiment\":\"NEUTRAL\",\"sentimentScore\":0,\"impactScore\":0.1,\"reason\":\"중립\"}"))
                .thenThrow(new RuntimeException("OpenAI error"));

        NewsAnalysisService.AnalysisBatchResult result = service.analyzePending(2);

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Parser rejects non JSON response")
    void parserRejectsNonJsonResponse() {
        assertThatThrownBy(() -> service.parseAnalysis("not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StockNews news() {
        return news(1L);
    }

    private StockNews news(Long id) {
        return StockNews.builder()
                .id(id)
                .stockCode("005930")
                .keyword("삼성전자")
                .title("삼성전자 실적 개선 기대")
                .summary("증권가가 실적 개선을 전망했다.")
                .publisher("테스트")
                .publishedAt(LocalDateTime.of(2026, 6, 5, 9, 0))
                .originUrl("https://example.com/news/" + id)
                .build();
    }

    private OpenAiResponse response(String text) {
        OpenAiResponse.ContentItem content = new OpenAiResponse.ContentItem("output_text", text);
        OpenAiResponse.OutputItem output = new OpenAiResponse.OutputItem(
                "message", "id", "completed", "assistant", List.of(content));
        OpenAiResponse.Usage usage = new OpenAiResponse.Usage(10, 20, 30);
        return new OpenAiResponse("resp", "completed", List.of(output), usage);
    }
}
