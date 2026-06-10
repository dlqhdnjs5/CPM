package com.bowon.cpm.scheduler;

import com.bowon.cpm.news.service.NewsAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsAnalysisSchedulerTest {

    @Mock SchedulerLogSupport logSupport;
    @Mock NewsAnalysisService newsAnalysisService;
    @InjectMocks NewsAnalysisScheduler scheduler;

    @Test
    @DisplayName("early morning news analysis uses the same limited pending analysis flow")
    void run_early_morning_analyzes_pending_news() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(false);
        when(logSupport.start(anyString())).thenReturn(30L);
        when(newsAnalysisService.analyzePending(50))
                .thenReturn(new NewsAnalysisService.AnalysisBatchResult(50, 45, 5));

        scheduler.runEarlyMorning();

        verify(newsAnalysisService).analyzePending(50);
        verify(logSupport).success(eq(30L), anyString());
    }

    @Test
    @DisplayName("news analysis scheduler skips on weekend")
    void run_skips_on_weekend() {
        when(logSupport.isWeekday()).thenReturn(false);

        scheduler.runEarlyMorning();

        verify(newsAnalysisService, never()).analyzePending(50);
        verify(logSupport, never()).start(anyString());
    }

    @Test
    @DisplayName("news analysis scheduler records failure")
    void run_records_failure() {
        when(logSupport.isWeekday()).thenReturn(true);
        when(logSupport.isAlreadyRunning(anyString())).thenReturn(false);
        when(logSupport.start(anyString())).thenReturn(40L);
        when(newsAnalysisService.analyzePending(50)).thenThrow(new RuntimeException("openai timeout"));

        scheduler.runEarlyMorning();

        verify(logSupport).fail(40L, "openai timeout");
    }
}
