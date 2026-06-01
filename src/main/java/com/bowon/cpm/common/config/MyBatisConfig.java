package com.bowon.cpm.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 설정
 * - *.mapper 패키지만 스캔한다. (다른 인터페이스가 Mapper로 오인되는 것 방지)
 * - 각 도메인의 mapper 패키지에 @Mapper 인터페이스를 두면 자동 등록된다.
 *   예) com.bowon.cpm.market.mapper, com.bowon.cpm.portfolio.mapper 등
 */
@Configuration
@MapperScan(basePackages = {
        "com.bowon.cpm.common.mapper",
        "com.bowon.cpm.stock.mapper",
        "com.bowon.cpm.market.mapper",
        "com.bowon.cpm.dart.mapper",
        "com.bowon.cpm.news.mapper",
        "com.bowon.cpm.ai.mapper",
        "com.bowon.cpm.risk.mapper",
        "com.bowon.cpm.order.mapper",
        "com.bowon.cpm.portfolio.mapper",
        "com.bowon.cpm.feedback.mapper"
})
public class MyBatisConfig {
}


