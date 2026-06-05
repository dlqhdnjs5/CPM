package com.bowon.cpm.dart.service;

import com.bowon.cpm.ai.client.OpenAiDecisionClient;
import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.dart.domain.DartDisclosure;
import com.bowon.cpm.dart.domain.DartMajorEvent;
import com.bowon.cpm.dart.mapper.DartDisclosureMapper;
import com.bowon.cpm.dart.mapper.DartMajorEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DartMajorEventServiceTest {

    @Mock DartDisclosureMapper disclosureMapper;
    @Mock DartMajorEventMapper majorEventMapper;
    @Mock OpenAiDecisionClient openAiClient;

    DartMajorEventService service;

    @BeforeEach
    void setUp() {
        OpenAiProperties openAiProperties = new OpenAiProperties(null, null, null, "gpt-test", null);
        service = new DartMajorEventService(disclosureMapper, majorEventMapper, openAiClient, openAiProperties);
    }

    @Test
    @DisplayName("Classifies Korean disclosure titles into major events")
    void classifyKoreanDisclosureTitles() {
        when(disclosureMapper.findByStockCodeAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        disclosure("001", "연결재무제표기준영업(잠정)실적(공정공시)"),
                        disclosure("002", "대표이사변경"),
                        disclosure("003", "타인에대한채무보증결정"),
                        disclosure("004", "주식등의대량보유상황보고서(약식)"),
                        disclosure("005", "분기보고서 (2026.03)")
                ));

        AtomicLong id = new AtomicLong(1);
        doAnswer(invocation -> {
            DartMajorEvent event = invocation.getArgument(0);
            event.setId(id.getAndIncrement());
            return null;
        }).when(majorEventMapper).insertIgnore(any(DartMajorEvent.class));
        doThrow(new RuntimeException("openai unavailable"))
                .when(openAiClient).createTextCompletion(anyString(), anyString(), anyString());

        int saved = service.classifyAndSave("036930");

        assertThat(saved).isEqualTo(4);

        ArgumentCaptor<DartMajorEvent> captor = ArgumentCaptor.forClass(DartMajorEvent.class);
        verify(majorEventMapper, times(4)).insertIgnore(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DartMajorEvent::getEventType)
                .containsExactlyInAnyOrder(
                        "EARNINGS_PREVIEW",
                        "CEO_CHANGE",
                        "DEBT_GUARANTEE",
                        "MAJOR_SHAREHOLDER"
                );
        verify(majorEventMapper, times(4)).updateSummary(any(Long.class), anyString());
    }

    private DartDisclosure disclosure(String receiptNo, String reportName) {
        return DartDisclosure.builder()
                .corpCode("00252135")
                .stockCode("036930")
                .corpName("주성엔지니어링")
                .receiptNo(receiptNo)
                .reportName(reportName)
                .disclosureDate(LocalDate.of(2026, 6, 5))
                .build();
    }
}
