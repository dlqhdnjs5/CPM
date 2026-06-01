package com.bowon.cpm.dart.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * OpenDART 공시 목록
 * 테이블: dart_disclosure
 */
@Getter
@Builder
public class DartDisclosure {
    private Long id;
    private String corpCode;
    private String stockCode;
    private String corpName;
    /** 접수번호 (UK) */
    private String receiptNo;
    /** 보고서명 */
    private String reportName;
    /** 공시 일자 */
    private LocalDate disclosureDate;
    /** 제출인 */
    private String submitter;
    /** 공시 URL */
    private String disclosureUrl;
    /** 중요 공시 여부 */
    private Boolean isImportant;
}

