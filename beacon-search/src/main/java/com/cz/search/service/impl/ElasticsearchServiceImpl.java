package com.cz.search.service.impl;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.SearchException;
import com.cz.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class ElasticsearchServiceImpl implements SearchService {
    /**
     * 添加成功的result
     */
    private final String CREATED = "created";

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    public void index(String index, String id, String json) throws IOException {
        //1、构建插入数据的Request
        IndexRequest request = new IndexRequest();

        //2、给request对象封装索引信息，文档id，以及文档内容
        request.index(index);
        request.id(id);
        request.source(json, XContentType.JSON);

        //3、将request信息发送给ES服务
        IndexResponse response = restHighLevelClient.index(request, RequestOptions.DEFAULT);

        //4、校验添加是否成功
        String result = response.getResult().getLowercase();
        if(!CREATED.equals(result)){
            // 添加失败！！
            log.error("【搜索模块-写入数据失败】 index = {},id = {},json = {},result = {}",index,id,json,result);
            throw new SearchException(ExceptionEnums.SEARCH_INDEX_ERROR);
        }
        log.info("【搜索模块-写入数据成功】 索引添加成功index = {},id = {},json = {},result = {}",index,id,json,result);
    }
}