package com.bowon.cpm.ai.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.ai.client.dto.OpenAiResponse;
import com.bowon.cpm.ai.domain.AiDecision;
import com.bowon.cpm.ai.domain.AiTradeDecisionJson;
import com.bowon.cpm.ai.mapper.AiDecisionFactorMapper;
import com.bowon.cpm.ai.mapper.AiDecisionMapper;
import com.bowon.cpm.ai.mapper.AiDecisionRawResponseMapper;
import com.bowon.cpm.ai.mapper.AiPromptLogMapper;
import com.bowon.cpm.ai.parser.AiDecisionParser;
import com.bowon.cpm.ai.prompt.AiDecisionPromptBuilder;
import com.bowon.cpm.broker.BrokerClient;
import com.bowon.cpm.broker.dto.StockQuoteResult;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.common.config.TradingProperties;
import com.bowon.cpm.common.mapper.ExternalApiCallLogMapper;
import com.bowon.cpm.dart.mapper.DartDisclosureMapper;
import com.bowon.cpm.dart.mapper.DartMajorEventMapper;
import com.bowon.cpm.dart.service.DartFinancialService;
import com.bowon.cpm.feedback.mapper.AiFeedbackMapper;
import com.bowon.cpm.feedback.mapper.AiPeriodicSummaryMapper;
import com.bowon.cpm.fundamental.service.FundamentalIndicatorService;
import com.bowon.cpm.market.mapper.StockIndicatorDailyMapper;
import com.bowon.cpm.market.mapper.StockPriceDailyMapper;
import com.bowon.cpm.news.mapper.StockNewsMapper;
import com.bowon.cpm.paper.domain.PaperAccountBalance;
import com.bowon.cpm.paper.service.PaperPortfolioService;
import com.bowon.cpm.portfolio.mapper.PortfolioPositionMapper;
import com.bowon.cpm.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDecisionServiceTest {

    @Mock OpenAiDecisionClient openAiClient;
    @Mock OpenAiProperties openAiProperties;
    @Mock AiDecisionPromptBuilder promptBuilder;
    @Mock AiDecisionParser parser;
    @Mock BrokerClient brokerClient;
    @Mock TradingProperties tradingProperties;
    @Mock PaperPortfolioService paperPortfolioService;
    @Mock KisProperties kisProperties;
    @Mock StockService stockService;
    @Mock StockPriceDailyMapper stockPriceDailyMapper;
    @Mock StockIndicatorDailyMapper stockIndicatorDailyMapper;
    @Mock StockNewsMapper stockNewsMapper;
    @Mock DartDisclosureMapper dartDisclosureMapper;
    @Mock DartMajorEventMapper dartMajorEventMapper;
    @Mock DartFinancialService dartFinancialService;
    @Mock FundamentalIndicatorService fundamentalIndicatorService;
    @Mock PortfolioPositionMapper portfolioPositionMapper;
    @Mock AiDecisionMapper decisionMapper;
    @Mock ExternalApiCallLogMapper externalApiCallLogMapper;
    @Mock AiFeedbackMapper aiFeedbackMapper;
    @Mock AiPeriodicSummaryMapper aiPeriodicSummaryMapper;
    @Mock AiPromptLogMapper promptLogMapper;
    @Mock AiDecisionRawResponseMapper rawResponseMapper;
    @Mock AiDecisionFactorMapper factorMapper;

    AiDecisionService service;

    @BeforeEach
    void setUp() {
        AiDecisionPersistService persistService = new AiDecisionPersistService(
                promptLogMapper,
                rawResponseMapper,
                decisionMapper,
                factorMapper
        );
        service = new AiDecisionService(
                openAiClient,
                openAiProperties,
                promptBuilder,
                parser,
                brokerClient,
                tradingProperties,
                paperPortfolioService,
                kisProperties,
                stockService,
                stockPriceDailyMapper,
                stockIndicatorDailyMapper,
                stockNewsMapper,
                dartDisclosureMapper,
                dartMajorEventMapper,
                dartFinancialService,
                fundamentalIndicatorService,
                portfolioPositionMapper,
                decisionMapper,
                externalApiCallLogMapper,
                aiFeedbackMapper,
                aiPeriodicSummaryMapper,
                persistService
        );
    }

    @Test
    @DisplayName("PAPER mode uses paper account balance in AI prompt and does not query KIS balance")
    void paperModeUsesPaperBalanceForPrompt() {
        when(stockService.findByStockCode("005930")).thenReturn(Optional.empty());
        when(stockPriceDailyMapper.findByStockCodeAndDateRange(anyString(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(stockNewsMapper.findByStockCodeAndPublishedAfter(anyString(), any(), any(Integer.class)))
                .thenReturn(Collections.emptyList());
        when(dartDisclosureMapper.findByStockCodeAndDateRange(anyString(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(dartMajorEventMapper.findByStockCodeAndDateAfter(anyString(), any(), any(Integer.class)))
                .thenReturn(Collections.emptyList());
        when(stockIndicatorDailyMapper.findLatestByStockCode("005930")).thenReturn(Optional.empty());
        when(fundamentalIndicatorService.findLatest("005930")).thenReturn(Optional.empty());
        when(paperPortfolioService.findPositionAsPortfolio("80710174", "005930")).thenReturn(null);
        when(aiFeedbackMapper.findRecentByStockCodeAndTypes(anyString(), any(List.class), any(Integer.class)))
                .thenReturn(Collections.emptyList());
        when(aiPeriodicSummaryMapper.findLatestBySummaryType(anyString())).thenReturn(Optional.empty());
        when(dartFinancialService.summarize("005930")).thenReturn(null);

        when(tradingProperties.isPaperMode()).thenReturn(true);
        when(tradingProperties.normalizedMode()).thenReturn("PAPER");
        when(kisProperties.accountNo()).thenReturn("80710174");
        when(paperPortfolioService.findLatestAccountBalance("80710174"))
                .thenReturn(Optional.of(PaperAccountBalance.builder()
                        .accountNo("80710174")
                        .baseDatetime(LocalDateTime.now())
                        .cashBalance(new BigDecimal("1000000"))
                        .availableCash(new BigDecimal("900000"))
                        .totalAssetAmount(new BigDecimal("1200000"))
                        .totalEvaluationAmount(new BigDecimal("300000"))
                        .totalProfitLossAmount(BigDecimal.ZERO)
                        .totalProfitLossRate(BigDecimal.ZERO)
                        .build()));
        when(brokerClient.getCurrentPrice("005930")).thenReturn(StockQuoteResult.builder()
                .stockCode("005930")
                .currentPrice(new BigDecimal("70000"))
                .build());

        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(5, BigDecimal.class))
                    .isEqualByComparingTo(new BigDecimal("1200000"));
            assertThat(invocation.getArgument(6, BigDecimal.class))
                    .isEqualByComparingTo(new BigDecimal("900000"));
            return "user";
        }).when(promptBuilder).buildUserPrompt(
                anyString(), anyString(),
                any(List.class), any(List.class), any(List.class),
                any(BigDecimal.class), any(BigDecimal.class),
                any(List.class), any(), any(List.class),
                any(), any(BigDecimal.class), any(), any(),
                any(), any(), anyString()
        );

        when(openAiProperties.modelDecision()).thenReturn("gpt-test");
        when(openAiClient.createDecision("system", "user"))
                .thenReturn(openAiResponse("json"));
        when(parser.parse("json")).thenReturn(new AiTradeDecisionJson(
                "005930",
                "005930",
                "HOLD",
                new BigDecimal("0.5"),
                new BigDecimal("70000"),
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                "LOW",
                "test",
                null,
                List.of()
        ));

        AiDecision decision = service.generateDecision("005930");

        assertThat(decision).isNotNull();
        verify(brokerClient, never()).getAccountBalance();
    }

    private OpenAiResponse openAiResponse(String text) {
        OpenAiResponse.ContentItem content = new OpenAiResponse.ContentItem("output_text", text);
        OpenAiResponse.OutputItem output = new OpenAiResponse.OutputItem(
                "message", "id", "completed", "assistant", List.of(content));
        return new OpenAiResponse("response", "completed", List.of(output), null);
    }
}
