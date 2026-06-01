package com.bowon.cpm.dart.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * OpenDART 기업 고유번호
 * 테이블: dart_corp_code
 */
@Getter
@Builder
public class DartCorpCode {
    /** DART 기업 고유번호 (8자리) */
    private String corpCode;
    /** 종목 코드 (상장 기업만 존재) */
    private String stockCode;
    /** 기업명 */
    private String corpName;
    /** 영문 기업명 */
    private String corpEngName;
    /** 최종 변경일 */
    private LocalDate modifyDate;
}

