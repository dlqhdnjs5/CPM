package com.bowon.cpm.dart.mapper;

import com.bowon.cpm.dart.domain.DartDisclosure;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DartDisclosureMapper {

    /** INSERT IGNORE — UK: receipt_no */
    void insertIgnore(DartDisclosure disclosure);

    /** 종목코드 + 기간으로 공시 조회 */
    List<DartDisclosure> findByStockCodeAndDateRange(
            @Param("stockCode") String stockCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}

