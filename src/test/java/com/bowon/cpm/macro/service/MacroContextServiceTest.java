package com.bowon.cpm.macro.service;

import com.bowon.cpm.macro.domain.MacroContext;
import com.bowon.cpm.news.domain.StockNews;
import com.bowon.cpm.news.mapper.StockNewsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroContextServiceTest {

    @Mock
    StockNewsMapper stockNewsMapper;

    @Test
    @DisplayName("latest macro context aggregates FED, BOK and market stance separately")
    void latest_context_aggregates_macro_stance() {
        when(stockNewsMapper.findByStockCodeAndPublishedAfter(eq(MacroNewsService.FED_CODE), any(), eq(20)))
                .thenReturn(List.of(news(
                        "연준 매파 발언으로 고금리 장기화 우려",
                        "higher for longer 기조가 유지될 수 있습니다.",
                        "NEGATIVE",
                        "-0.7",
                        "0.9"
                )));
        when(stockNewsMapper.findByStockCodeAndPublishedAfter(eq(MacroNewsService.BOK_CODE), any(), eq(20)))
                .thenReturn(List.of(news(
                        "한국은행 금리 인하 가능성 언급",
                        "경기 둔화에 대응한 완화 기대가 커졌습니다.",
                        "POSITIVE",
                        "0.4",
                        "0.6"
                )));
        when(stockNewsMapper.findByStockCodeAndPublishedAfter(eq(MacroNewsService.MARKET_CODE), any(), eq(20)))
                .thenReturn(List.of(news(
                        "원달러 환율 급등과 위험 회피 심리",
                        "risk off 분위기가 강해졌습니다.",
                        "NEGATIVE",
                        "-0.5",
                        "0.7"
                )));

        MacroContext context = new MacroContextService(stockNewsMapper).latestContext(7, 20);

        assertThat(context.fed().stance()).isEqualTo("HAWKISH");
        assertThat(context.bok().stance()).isEqualTo("DOVISH");
        assertThat(context.market().stance()).isEqualTo("RISK_OFF");
        assertThat(context.fed().sentimentScore()).isEqualByComparingTo("-0.700000");
        assertThat(context.combinedRiskScore()).isNotNull();
        assertThat(context.combinedRiskScore()).isPositive();
    }

    @Test
    @DisplayName("empty macro news returns neutral signals without failing AI prompt creation")
    void empty_news_returns_neutral_signals() {
        when(stockNewsMapper.findByStockCodeAndPublishedAfter(any(), any(), eq(20)))
                .thenReturn(List.of());

        MacroContext context = new MacroContextService(stockNewsMapper).latestContext(7, 20);

        assertThat(context.fed().stance()).isEqualTo("NEUTRAL");
        assertThat(context.fed().newsCount()).isZero();
        assertThat(context.combinedRiskScore()).isNull();
    }

    private StockNews news(String title, String summary, String sentiment, String sentimentScore, String impactScore) {
        return StockNews.builder()
                .title(title)
                .summary(summary)
                .sentiment(sentiment)
                .sentimentScore(new BigDecimal(sentimentScore))
                .impactScore(new BigDecimal(impactScore))
                .publishedAt(LocalDateTime.of(2026, 6, 9, 9, 0))
                .build();
    }
}
