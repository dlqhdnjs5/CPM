package com.bowon.cpm.macro.service;

import com.bowon.cpm.macro.domain.MacroContext;
import com.bowon.cpm.support.TestProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ActiveProfiles(TestProfiles.TEST)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MacroContextService.class)
class MacroContextServiceIntegrationTest {

    @Autowired MacroContextService macroContextService;

    @Test
    @DisplayName("macro context query works through MyBatis against the configured database")
    void macro_context_query_works_with_database() {
        MacroContext context = macroContextService.latestContext(7, 5);

        assertThat(context).isNotNull();
        assertThat(context.fed().code()).isEqualTo(MacroNewsService.FED_CODE);
        assertThat(context.bok().code()).isEqualTo(MacroNewsService.BOK_CODE);
        assertThat(context.market().code()).isEqualTo(MacroNewsService.MARKET_CODE);
    }
}
