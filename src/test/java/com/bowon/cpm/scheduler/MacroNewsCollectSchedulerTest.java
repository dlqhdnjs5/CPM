package com.bowon.cpm.scheduler;

import com.bowon.cpm.macro.service.MacroNewsService;
import com.bowon.cpm.news.service.NewsAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroNewsCollectSchedulerTest {

    @Mock SchedulerLogSupport logSupport;
    @Mock MacroNewsService macroNewsService;
    @InjectMocks MacroNewsCollectScheduler scheduler;

    @Test
    @DisplayName("macro scheduler collects only, leaving analysis to NewsAnalysisScheduler")
    void run_collects_macro_news_without_analysis() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(false);
        when(logSupport.start(anyString())).thenReturn(10L);
        when(macroNewsService.collectDefault(eq(3), eq(false), eq(0)))
                .thenReturn(new MacroNewsService.MacroCollectResult(
                        12,
                        Map.of(MacroNewsService.FED_CODE, 4),
                        new NewsAnalysisService.AnalysisBatchResult(0, 0, 0)
                ));

        scheduler.run(3);

        verify(macroNewsService).collectDefault(3, false, 0);
        verify(logSupport).success(eq(10L), anyString());
    }

    @Test
    @DisplayName("macro scheduler skips on weekend")
    void run_skips_on_weekend() {
        when(logSupport.isWeekday()).thenReturn(false);

        scheduler.run(3);

        verify(macroNewsService, never()).collectDefault(eq(3), eq(false), eq(0));
        verify(logSupport, never()).start(anyString());
    }

    @Test
    @DisplayName("macro scheduler skips when already running")
    void run_skips_when_already_running() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(true);

        scheduler.run(3);

        verify(macroNewsService, never()).collectDefault(eq(3), eq(false), eq(0));
        verify(logSupport, never()).start(anyString());
    }

    @Test
    @DisplayName("macro scheduler records failure when collection fails")
    void run_records_failure() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(false);
        when(logSupport.start(anyString())).thenReturn(20L);
        when(macroNewsService.collectDefault(eq(2), eq(false), eq(0)))
                .thenThrow(new RuntimeException("naver timeout"));

        scheduler.run(2);

        verify(logSupport).fail(20L, "naver timeout");
    }
}
