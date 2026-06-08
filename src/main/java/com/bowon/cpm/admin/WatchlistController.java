package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.watchlist.domain.StockCandidateScore;
import com.bowon.cpm.watchlist.service.WatchlistDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistDiscoveryService watchlistDiscoveryService;

    @PostMapping("/discover")
    public ApiResponse<WatchlistDiscoveryService.DiscoveryResult> discover(
            @RequestParam(defaultValue = "true") boolean bootstrap,
            @RequestParam(defaultValue = "20") int newsAnalyzeLimit
    ) {
        WatchlistDiscoveryService.DiscoveryResult result =
                watchlistDiscoveryService.discover(bootstrap, newsAnalyzeLimit);
        return ApiResponse.ok("Watchlist discovery completed", result);
    }

    @GetMapping("/candidates")
    public ApiResponse<List<StockCandidateScore>> candidates(
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(watchlistDiscoveryService.findLatestCandidates(limit));
    }
}
