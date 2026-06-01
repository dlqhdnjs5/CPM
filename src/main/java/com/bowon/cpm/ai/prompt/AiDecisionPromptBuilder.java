package com.bowon.cpm.ai.prompt;

import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.feedback.domain.AiFeedback;
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
                - 기술적 지표 (이동평균, 거래량, 가격 흐름)
                - 최근 뉴스 감성
                - 공시 내용
                - 현재 가격 대비 기대수익률 / 예상손실률

                규칙:
                - 데이터가 부족하면 반드시 HOLD를 반환한다.
                - 보수적으로 판단한다. 불확실하면 HOLD다.
                - targetPrice는 BUY 시 현재가보다 높아야 한다.
                - stopLossPrice는 BUY 시 현재가보다 낮아야 한다.
                - confidence는 0.0~1.0 사이의 소수다.
                - recommendedPortfolioWeight는 0.0~1.0 사이다 (예: 0.15 = 15%).
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
            List<DartMajorEvent> majorEvents
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래 데이터를 분석해서 매매 판단 JSON을 생성하라.\n\n");

        // 종목 기본 정보
        sb.append("## 종목 정보\n");
        sb.append("종목코드: ").append(stockCode).append("\n");
        sb.append("종목명: ").append(stockName).append("\n\n");

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

        // 최근 일봉 (최대 20일)
        sb.append("## 최근 일봉 (최신순)\n");
        if (dailyPrices.isEmpty()) {
            sb.append("데이터 없음\n");
        } else {
            sb.append("날짜 | 시가 | 고가 | 저가 | 종가 | 거래량\n");
            dailyPrices.stream().limit(20).forEach(p ->
                    sb.append(p.getTradeDate()).append(" | ")
                            .append(p.getOpenPrice()).append(" | ")
                            .append(p.getHighPrice()).append(" | ")
                            .append(p.getLowPrice()).append(" | ")
                            .append(p.getClosePrice()).append(" | ")
                            .append(p.getVolume()).append("\n")
            );
        }
        sb.append("\n");

        // 최근 뉴스 (최대 10건)
        sb.append("## 최근 뉴스\n");
        if (newsList.isEmpty()) {
            sb.append("데이터 없음\n");
        } else {
            newsList.stream().limit(10).forEach(n ->
                    sb.append("- [").append(n.getPublishedAt() != null
                                    ? n.getPublishedAt().toLocalDate() : "날짜미상")
                            .append("] ").append(n.getTitle()).append("\n")
                            .append("  요약: ").append(n.getSummary()).append("\n")
            );
        }
        sb.append("\n");

        // 최근 공시 (최대 5건)
        sb.append("## 최근 공시\n");
        if (disclosures.isEmpty()) {
            sb.append("데이터 없음\n");
        } else {
            disclosures.stream().limit(5).forEach(d ->
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

        // 주요 이벤트 (최대 5건)
        sb.append("## ⚠️ 주요 이벤트\n");
        if (majorEvents == null || majorEvents.isEmpty()) {
            sb.append("최근 6개월 주요 이벤트 없음\n");
        } else {
            majorEvents.stream().limit(5).forEach(e ->
                    sb.append("- [").append(e.getEventDate()).append("] ")
                            .append(e.getEventType()).append(": ")
                            .append(e.getSummary() != null ? e.getSummary() : e.getEventTitle())
                            .append("\n")
            );
        }
        sb.append("\n");

        // 과거 AI 판단 피드백 (최대 3건) — 과거 판단 결과를 참고해 판단 품질 개선
        sb.append("## 과거 판단 피드백\n");
        if (recentFeedbacks == null || recentFeedbacks.isEmpty()) {
            sb.append("피드백 없음 (첫 판단)\n");
        } else {
            recentFeedbacks.stream().limit(3).forEach(f ->
                    sb.append("- ").append(f.getFeedbackSummary() != null
                            ? f.getFeedbackSummary() : "요약 없음").append("\n")
            );
        }

        return sb.toString();
    }
}

