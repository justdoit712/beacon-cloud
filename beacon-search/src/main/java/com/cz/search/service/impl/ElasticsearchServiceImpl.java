package com.cz.search.service.impl;

import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.SearchException;
import com.cz.common.model.StandardReport;
import com.cz.search.service.SearchService;
import com.cz.search.utils.SearchUtils;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.elasticsearch.action.DocWriteResponse.Result;

import java.io.IOException;
import java.util.*;

import static org.elasticsearch.action.DocWriteResponse.Result.UPDATED;

@Service
@Slf4j
public class ElasticsearchServiceImpl implements SearchService {
    /**
     * 添加成功的result
     */
    private final String CREATED = "created";

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

    @Override
    public boolean exists(String index, String id) throws IOException {
        // 构建GetRequest，查看索引是否存在
        GetRequest request = new GetRequest();

        // 指定索引信息，还有文档id
        request.index(index);
        request.id(id);

        // 基于restHighLevelClient将查询指定id的文档是否存在的请求投递过去。
        boolean exists = restHighLevelClient.exists(request, RequestOptions.DEFAULT);

        // 直接返回信息
        return exists;
    }


    @Override
    public void update(String index, String id, Map<String, Object> doc) throws IOException {
        // 1、基于exists方法，查询当前文档是否存在
        boolean exists = exists(index, id);
        if (!exists) {
            // 当前文档不存在
            StandardReport report = SearchUtils.get();
            if (report.getReUpdate()) {
                // 第二次获取投递的消息，到这已经是延迟20s了。
                log.error("【搜索模块-修改日志】 修改日志失败，文档不存在 report = {}", report);
            } else {
                // 第一次投递，可以再次将消息仍会MQ中
                // 开始第二次消息的投递了
                report.setReUpdate(true);
                rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_GATEWAY_NORMAL_QUEUE, report);
            }
            SearchUtils.remove();
            return;
        }

        // 2、到这，可以确认文档是存在的，直接做修改操作
        UpdateRequest request = new UpdateRequest();
        request.index(index);
        request.id(id);
        request.doc(doc);

        // 执行更新
        UpdateResponse response = restHighLevelClient.update(request, RequestOptions.DEFAULT);

        // 获取结果枚举（不要转成String，直接对比枚举）
        Result result = response.getResult();

        // 3、校验结果：成功(UPDATED) 或 无变化(NOOP) 都算成功
        if (result == Result.UPDATED || result == Result.NOOP) {
            log.info("【搜索模块-修改日志成功】 文档修改成功 index = {}, id = {}, result = {}, doc = {}", index, id, result, doc);
        } else {
            // 其他状态视为失败
            log.error("【搜索模块-修改日志失败】 index = {}, id = {}, result = {}, doc = {}", index, id, result, doc);
            throw new SearchException(ExceptionEnums.SEARCH_UPDATE_ERROR);
        }
    }

}