package com.bowon.cpm.common.config;

import com.bowon.cpm.ai.client.OpenAiProperties;
import com.bowon.cpm.broker.kis.KisProperties;
import com.bowon.cpm.dart.client.DartProperties;
import com.bowon.cpm.news.client.NaverProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        KisProperties.class,
        DartProperties.class,
        NaverProperties.class,
        OpenAiProperties.class,
        TradingProperties.class
})
public class PropertiesConfig {
}

