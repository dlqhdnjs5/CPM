package com.bowon.cpm.ai.prompt;

import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.feedback.domain.AiFeedback;
import com.bowon.cpm.feedback.domain.AiPeriodicSummary;
import java.time.format.DateTimeFormatter;
import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import com.bowon.cpm.news.domain.StockNews;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 매매 판단 프롬프트 빌더
 */
@Component
public class AiDecisionPromptBuilder {

    public String buildSystemPrompt() {
        return """
                너는 한국 주식 자동매매 시스템의 AI 판단 엔진이다.

                반드시 지정된 JSON Schema에 맞는 응답만 반환한다.
                마크다운, 설명문, 코드블록은 절대 포함하지 않는다. 순수 JSON만 반환한다.

                판단 기준:
                - 기술적 지표 (이동평균, RSI, MACD, 볼린저밴드, 거래량)
                - 최근 뉴스 감성
                - 공시/주요 이벤트
                - 재무 상태
                - 현재가 대비 기대수익률/예상손실률

                응답 필드 가이드:
                - decision: BUY / SELL / HOLD 중 하나
                - currentPrice: 입력으로 제공된 "현재가(실시간)"을 우선 사용. 없으면 최근 일봉 종가 사용.
                - targetPrice: BUY 시 현재가보다 높게, SELL 시 현재가보다 낮게 설정한다.
                - stopLossPrice: BUY 시 현재가보다 낮게, SELL 시 현재가보다 높게 설정한다.
                - confidence: 0.0~1.0 (데이터 부족/판단 모호 시 0.6 이하로 낮춰라)
                - recommendedPortfolioWeight: 0.0~1.0 (예: 0.15 = 15%)
                - expectedHoldingDays: 예상 보유 거래일(영업일 기준 권장)
                - riskLevel: LOW / MEDIUM / HIGH
                - analysis: 영역별 분석 (technical/news/disclosure/fundamental/supplyDemand).
                  데이터 없는 영역은 null 로 둔다.
                - factors: 판단에 영향을 준 요인을 1개 이상 배열로 반환한다.
                  각 factor 의 type 은 TECHNICAL / NEWS / DART / FUNDAMENTAL / SUPPLY_DEMAND 중 하나.
                  direction 은 POSITIVE / NEGATIVE / NEUTRAL.
                  score 는 0.0~1.0 의 영향력.

                규칙:
                - 데이터가 부족하면 반드시 HOLD를 반환하고 confidence 를 낮춘다.
                - 보수적으로 판단한다. 불확실하면 HOLD.
                - 절대 마크다운/설명문/코드블록을 포함하지 않는다. JSON만.
                """;
    }

    /**
     * 종목 분석 데이터로 사용자 프롬프트 생성
     *
     * @param stockCode       종목 코드
     * @param stockName       종목명
     * @param dailyPrices     최근 일봉 (최신순)
     * @param newsList        최근 뉴스
     * @param disclosures     최근 공시
     * @param totalAsset      총 평가자산 (원, null 허용)
     * @param availableCash   주문 가능 예수금 (원, null 허용)
     * @param recentFeedbacks 최근 피드백 목록 (null 또는 빈 리스트 허용)
     * @param financialSummary 재무 요약 텍스트 (null 허용)
     * @param majorEvents     주요 이벤트 목록 (null 또는 빈 리스트 허용)
     * @param indicator      기술적 지표 최신 1건 (null 허용)
     * @param realtimeQuote  실시간 현재가 (null 허용)
     * @param weeklySummary  최근 WEEKLY 요약 1건 (ai_periodic_summary, null 허용)
     * @param monthlySummary 최근 MONTHLY 요약 1건 (ai_periodic_summary, null 허용)
     */
    public String buildUserPrompt(
            String stockCode,
            String stockName,
            List<StockPriceDaily> dailyPrices,
            List<StockNews> newsList,
            List<DartDisclosure> disclosures,
            BigDecimal totalAsset,
            BigDecimal availableCash,
            List<AiFeedback> recentFeedbacks,
            String financialSummary,
            List<DartMajorEvent> majorEvents,
            StockIndicatorDaily indicator,
            BigDecimal realtimeQuote,
            AiPeriodicSummary weeklySummary,
            AiPeriodicSummary monthlySummary
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래 데이터를 분석해서 매매 판단 JSON을 생성하라.\n\n");

        // 종목 기본 정보
        sb.append("## 종목 정보\n");
        sb.append("종목코드: ").append(stockCode).append("\n");
        sb.append("종목명: ").append(stockName).append("\n");
        if (realtimeQuote != null) {
            sb.append("현재가(실시간): ").append(String.format("%,.0f", realtimeQuote)).append("원\n");
        }
        sb.append("\n");

        // 계좌 정보 — recommendedPortfolioWeight 결정에 활용
        sb.append("## 계좌 정보\n");
        if (totalAsset != null) {
            sb.append("총 평가자산: ").append(String.format("%,.0f", totalAsset)).append("원\n");
        } else {
            sb.append("총 평가자산: 정보 없음\n");
        }
        if (availableCash != null) {
            sb.append("주문 가능 예수금: ").append(String.format("%,.0f", availableCash)).append("원\n");
            if (totalAsset != null && totalAsset.compareTo(BigDecimal.ZERO) > 0) {
                double cashRatio = availableCash.doubleValue() / totalAsset.doubleValue() * 100;
                sb.append("예수금 비중: ").append(String.format("%.1f", cashRatio)).append("%\n");
            }
        } else {
            sb.append("주문 가능 예수금: 정보 없음\n");
        }
        sb.append("※ recommendedPortfolioWeight는 총 평가자산 대비 비중으로, 실제 주문 가능 금액(예수금)을 초과하지 않도록 판단하라.\n\n");

        // 기술적 지표
        sb.append("## 기술적 지표 (최신)\n");
        if (indicator == null) {
            sb.append("데이터 없음\n");
        } else {
            sb.append("기준일: ").append(indicator.getTradeDate()).append("\n");
            appendIfNotNull(sb, "MA5", indicator.getMa5());
            appendIfNotNull(sb, "MA20", indicator.getMa20());
            appendIfNotNull(sb, "MA60", indicator.getMa60());
            appendIfNotNull(sb, "MA120", indicator.getMa120());
            appendIfNotNull(sb, "RSI14", indicator.getRsi14());
            appendIfNotNull(sb, "MACD", indicator.getMacd());
            appendIfNotNull(sb, "MACD Signal", indicator.getMacdSignal());
            appendIfNotNull(sb, "Bollinger Upper", indicator.getBollingerUpper());
            appendIfNotNull(sb, "Bollinger Middle", indicator.getBollingerMiddle());
            appendIfNotNull(sb, "Bollinger Lower", indicator.getBollingerLower());
            appendIfNotNull(sb, "Volatility", indicator.getVolatility());
        }
        sb.append("\n");

        // 최근 일봉 (최대 30일 표시 — 60일 수집 중 최신 30일만 노출하여 토큰 절감)
        sb.append("## 최근 일봉 (최신순, 최대 30일)\n");
        if (dailyPrices.isEmpty()) {
            sb.append("데이터 없음\n");
        } else {
            sb.append("날짜 | 시가 | 고가 | 저가 | 종가 | 거래량\n");
            dailyPrices.stream().limit(30).forEach(p ->
                    sb.append(p.getTradeDate()).append(" | ")
                            .append(p.getOpenPrice()).append(" | ")
                            .append(p.getHighPrice()).append(" | ")
                            .append(p.getLowPrice()).append(" | ")
                            .append(p.getClosePrice()).append(" | ")
                            .append(p.getVolume()).append("\n")
            );
        }
        sb.append("\n");

        // 최근 뉴스
        sb.append("## 최근 뉴스 (7일 이내)\n");
        if (newsList.isEmpty()) {
            sb.append("데이터 없음\n");
        } else {
            newsList.stream().limit(15).forEach(n ->
                    sb.append("- [").append(n.getPublishedAt() != null
                                    ? n.getPublishedAt().toLocalDate() : "날짜미상")
                            .append("] ").append(n.getTitle()).append("\n")
                            .append("  요약: ").append(n.getSummary()).append("\n")
                            .append(newsSentimentLine(n))
            );
        }
        sb.append("\n");

        // 공시
        sb.append("## 최근 공시\n");
        if (disclosures.isEmpty()) {
            sb.append("데이터 없음\n");
        } else {
            disclosures.stream().limit(10).forEach(d ->
                    sb.append("- [").append(d.getDisclosureDate()).append("] ")
                            .append(d.getReportName()).append("\n")
            );
        }
        sb.append("\n");

        // 재무 요약
        sb.append("## 재무 요약\n");
        if (financialSummary != null && !financialSummary.isBlank()) {
            sb.append(financialSummary).append("\n");
        } else {
            sb.append("데이터 없음\n");
        }
        sb.append("\n");

        // 주요 이벤트
        sb.append("## ⚠️ 주요 이벤트\n");
        if (majorEvents == null || majorEvents.isEmpty()) {
            sb.append("최근 3개월 주요 이벤트 없음\n");
        } else {
            majorEvents.stream().limit(10).forEach(e ->
                    sb.append("- [").append(e.getEventDate()).append("] ")
                            .append(e.getEventType()).append(": ")
                            .append(e.getSummary() != null ? e.getSummary() : e.getEventTitle())
                            .append("\n")
            );
        }
        sb.append("\n");

        // 과거 AI 판단 피드백 (DAILY 제외 — WEEKLY/MONTHLY/HOLDING_END)
        sb.append("## 과거 판단 피드백 (DAILY 제외)\n");
        if (recentFeedbacks == null || recentFeedbacks.isEmpty()) {
            sb.append("피드백 없음 (첫 판단 또는 평가 미진행)\n");
        } else {
            recentFeedbacks.stream().limit(3).forEach(f ->
                    sb.append("- [").append(f.getEvaluationType()).append("] ")
                            .append(f.getFeedbackSummary() != null
                                    ? f.getFeedbackSummary() : "요약 없음").append("\n")
            );
        }
        sb.append("\n");

        // Plan 14 Phase 5: 주간 / 월간 LLM 요약 (ai_periodic_summary)
        sb.append("## [참고: 지난주 WEEKLY 요약]\n");
        appendPeriodicSummary(sb, weeklySummary);
        sb.append("\n");

        sb.append("## [참고: 지난달 MONTHLY 전략 개선 제안]\n");
        appendPeriodicSummary(sb, monthlySummary);

        return sb.toString();
    }

    /**
     * 하위호환용 오버로드 (WEEKLY/MONTHLY 요약 생략).
     */
    public String buildUserPrompt(
            String stockCode,
            String stockName,
            List<StockPriceDaily> dailyPrices,
            List<StockNews> newsList,
            List<DartDisclosure> disclosures,
            BigDecimal totalAsset,
            BigDecimal availableCash,
            List<AiFeedback> recentFeedbacks,
            String financialSummary,
            List<DartMajorEvent> majorEvents,
            StockIndicatorDaily indicator,
            BigDecimal realtimeQuote
    ) {
        return buildUserPrompt(stockCode, stockName, dailyPrices, newsList, disclosures,
                totalAsset, availableCash, recentFeedbacks, financialSummary,
                majorEvents, indicator, realtimeQuote, null, null);
    }

    private void appendPeriodicSummary(StringBuilder sb, AiPeriodicSummary s) {
        if (s == null || s.getLlmSummary() == null || s.getLlmSummary().isBlank()) {
            sb.append("요약 없음 (아직 집계되지 않음)\n");
            return;
        }
        DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE;
        sb.append("기간: ").append(s.getPeriodStart() != null ? s.getPeriodStart().format(df) : "?")
                .append(" ~ ").append(s.getPeriodEnd() != null ? s.getPeriodEnd().format(df) : "?")
                .append("\n");
        sb.append(s.getLlmSummary().trim()).append("\n");
    }

    private void appendIfNotNull(StringBuilder sb, String label, Object value) {
        if (value != null) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private String newsSentimentLine(StockNews news) {
        if (news.getSentiment() == null) {
            return "";
        }
        return "  sentiment: " + news.getSentiment()
                + ", sentimentScore=" + news.getSentimentScore()
                + ", impactScore=" + news.getImpactScore()
                + "\n";
    }
}
