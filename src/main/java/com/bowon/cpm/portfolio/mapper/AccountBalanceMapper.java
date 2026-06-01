package com.bowon.cpm.portfolio.mapper;

import com.bowon.cpm.portfolio.domain.AccountBalance;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AccountBalanceMapper {
    void insert(AccountBalance accountBalance);

    /** 계좌번호 기준 최신 잔고 1건 조회 */
    List<AccountBalance> findLatestByAccountNo(String accountNo);
}
