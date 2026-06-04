package com.bowon.cpm.news.mapper;

import com.bowon.cpm.news.domain.NewsSentiment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NewsSentimentMapper {
    void insertIgnore(NewsSentiment sentiment);
}
