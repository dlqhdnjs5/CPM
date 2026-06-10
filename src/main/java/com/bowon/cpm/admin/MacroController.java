package com.bowon.cpm.admin;

import com.bowon.cpm.common.response.ApiResponse;
import com.bowon.cpm.macro.domain.MacroContext;
import com.bowon.cpm.macro.service.MacroContextService;
import com.bowon.cpm.macro.service.MacroNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/macro")
@RequiredArgsConstructor
public class MacroController {

    private final MacroNewsService macroNewsService;
    private final MacroContextService macroContextService;

    @PostMapping("/news/fetch")
    public ApiResponse<MacroNewsService.MacroCollectResult> fetchMacroNews(
            @RequestParam(defaultValue = "10") int displayPerKeyword,
            @RequestParam(defaultValue = "true") boolean analyze,
            @RequestParam(defaultValue = "80") int analyzeLimit
    ) {
        MacroNewsService.MacroCollectResult result =
                macroNewsService.collectDefault(displayPerKeyword, analyze, analyzeLimit);
        return ApiResponse.ok("매크로 뉴스 수집 완료", result);
    }

    @GetMapping("/context/latest")
    public ApiResponse<MacroContext> getLatestMacroContext(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limitPerSignal
    ) {
        return ApiResponse.ok(macroContextService.latestContext(days, limitPerSignal));
    }
}
