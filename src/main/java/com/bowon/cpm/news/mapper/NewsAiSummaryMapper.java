package com.bowon.cpm.news.mapper;

import com.bowon.cpm.news.domain.NewsAiSummary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NewsAiSummaryMapper {
    void insertIgnore(NewsAiSummary summary);
}
