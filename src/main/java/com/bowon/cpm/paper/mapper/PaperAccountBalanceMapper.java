package com.bowon.cpm.paper.mapper;

import com.bowon.cpm.paper.domain.PaperAccountBalance;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PaperAccountBalanceMapper {
    void insert(PaperAccountBalance accountBalance);

    List<PaperAccountBalance> findLatestByAccountNo(String accountNo);
}
