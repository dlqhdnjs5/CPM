package com.bowon.cpm.dart.mapper;

import com.bowon.cpm.dart.domain.DartStockQuantity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DartStockQuantityMapper {

    int upsert(DartStockQuantity stockQuantity);

    Optional<DartStockQuantity> findLatestByStockCode(@Param("stockCode") String stockCode);

    List<DartStockQuantity> findByStockCode(@Param("stockCode") String stockCode,
                                            @Param("limit") int limit);
}
