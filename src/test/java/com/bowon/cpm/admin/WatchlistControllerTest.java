package com.bowon.cpm.admin;

import com.bowon.cpm.watchlist.service.WatchlistDiscoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

    @Autowired MockMvc mvc;

    @MockBean WatchlistDiscoveryService watchlistDiscoveryService;

    @Test
    @DisplayName("POST /api/admin/watchlist/discover delegates to service")
    void discover() throws Exception {
        when(watchlistDiscoveryService.discover(eq(false), eq(0)))
                .thenReturn(new WatchlistDiscoveryService.DiscoveryResult(
                        LocalDate.of(2026, 6, 6),
                        30,
                        30,
                        20,
                        5,
                        10,
                        12,
                        10,
                        false,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of()
                ));

        mvc.perform(post("/api/admin/watchlist/discover")
                        .param("bootstrap", "false")
                        .param("newsAnalyzeLimit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoredCount").value(30))
                .andExpect(jsonPath("$.data.maxWatchedStocks").value(20));

        verify(watchlistDiscoveryService).discover(false, 0);
    }

    @Test
    @DisplayName("GET /api/admin/watchlist/candidates delegates to service")
    void candidates() throws Exception {
        when(watchlistDiscoveryService.findLatestCandidates(eq(10))).thenReturn(List.of());

        mvc.perform(get("/api/admin/watchlist/candidates").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(watchlistDiscoveryService).findLatestCandidates(10);
    }
}
