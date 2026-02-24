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
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
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
        // 1、构建插入数据的Request
        IndexRequest request = new IndexRequest();

        // 2、给request对象封装索引信息，文档id，以及文档内容
        request.index(index);
        request.id(id);
        request.source(json, XContentType.JSON);

        // 3、将request信息发送给ES服务
        IndexResponse response = restHighLevelClient.index(request, RequestOptions.DEFAULT);

        // 4、校验添加是否成功
        String result = response.getResult().getLowercase();
        if (!CREATED.equals(result)) {
            // 添加失败！！
            log.error("【搜索模块-写入数据失败】 index = {},id = {},json = {},result = {}", index, id, json, result);
            throw new SearchException(ExceptionEnums.SEARCH_INDEX_ERROR);
        }
        log.info("【搜索模块-写入数据成功】 索引添加成功index = {},id = {},json = {},result = {}", index, id, json, result);
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

    @Override
    public Map<String, Object> findSmsByParameters(Map<String, Object> parameters) throws IOException {
        SearchRequest request = new SearchRequest(SearchUtils.getCurrYearIndex(), "");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 1. 构建通用查询条件
        BoolQueryBuilder boolQuery = buildBoolQuery(parameters, sourceBuilder);
        sourceBuilder.query(boolQuery);

        // 2. 分页查询
        Object fromObj = parameters.get("from");
        Object sizeObj = parameters.get("size");
        if (fromObj != null && sizeObj != null) {
            sourceBuilder.from(Integer.parseInt(fromObj + ""));
            sourceBuilder.size(Integer.parseInt(sizeObj + ""));
        }

        request.source(sourceBuilder);

        // 3. 执行查询
        SearchResponse resp = restHighLevelClient.search(request, RequestOptions.DEFAULT);

        // 4. 封装数据
        long total = resp.getHits().getTotalHits().value;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SearchHit hit : resp.getHits().getHits()) {
            Map<String, Object> row = hit.getSourceAsMap();
            List sendTime = (List) row.get("sendTime");
            String sendTimeStr = listToDateString(sendTime);
            row.put("sendTimeStr", sendTimeStr);
            row.put("corpname", row.get("sign"));
            HighlightField highlightField = hit.getHighlightFields().get("text");
            if (highlightField != null) {
                String textHighLight = highlightField.getFragments()[0].toString();
                row.put("text", textHighLight);
            }
            rows.add(row);
        }

        // 5. 返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    /**
     * 专门用于饼图的聚合统计方法
     */
    public Map<String, Integer> countSmsState(Map<String, Object> parameters) throws IOException {
        SearchRequest request = new SearchRequest(SearchUtils.getCurrYearIndex(), "");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 1. 构建通用查询条件 (不需要高亮，所以传 null)
        BoolQueryBuilder boolQuery = buildBoolQuery(parameters, null);
        sourceBuilder.query(boolQuery);

        // 2. 核心：size 设置为 0，不返回任何明细文档，突破 10000 限制！
        sourceBuilder.size(0);

        // 3. 构建聚合条件：按照 reportState 字段进行 terms 分组聚合
        TermsAggregationBuilder stateAgg = AggregationBuilders.terms("state_group").field("reportState");
        sourceBuilder.aggregation(stateAgg);

        request.source(sourceBuilder);

        // 4. 执行查询
        SearchResponse resp = restHighLevelClient.search(request, RequestOptions.DEFAULT);

        // 5. 解析聚合结果
        Map<String, Integer> resultMap = new HashMap<>();
        resultMap.put("waiting", 0);
        resultMap.put("success", 0);
        resultMap.put("fail", 0);

        Terms terms = resp.getAggregations().get("state_group");
        if (terms != null) {
            for (Terms.Bucket bucket : terms.getBuckets()) {
                int state = bucket.getKeyAsNumber().intValue();
                int count = (int) bucket.getDocCount();

                if (state == 1) {
                    resultMap.put("success", count);
                } else if (state == 2) {
                    resultMap.put("fail", count);
                } else {
                    // 状态 0 或其他，算作 waiting
                    resultMap.put("waiting", resultMap.get("waiting") + count);
                }
            }
        }
        return resultMap;
    }

    /**
     * 【提取】：提取公共的条件组装逻辑，复用于列表查询和统计查询
     */
    private BoolQueryBuilder buildBoolQuery(Map<String, Object> parameters, SearchSourceBuilder sourceBuilder) {
        Object contentObj = parameters.get("content");
        Object mobileObj = parameters.get("mobile");
        Object startTimeObj = parameters.get("starttime");
        Object stopTimeObj = parameters.get("stoptime");
        Object clientIDObj = parameters.get("clientID");

        List<Long> clientIDList = null;
        if (clientIDObj instanceof List) {
            clientIDList = (List) clientIDObj;
        } else if (!ObjectUtils.isEmpty(clientIDObj)) {
            clientIDList = Collections.singletonList(Long.parseLong(clientIDObj + ""));
        }

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 关键字 (文本搜索及高亮)
        if (!ObjectUtils.isEmpty(contentObj)) {
            boolQuery.must(QueryBuilders.matchQuery("text", contentObj));
            // 只有当传入了 sourceBuilder（列表查询时），才去配置高亮
            if (sourceBuilder != null) {
                HighlightBuilder highlightBuilder = new HighlightBuilder();
                highlightBuilder.field("text");
                highlightBuilder.preTags("<span style='color: red'>");
                highlightBuilder.postTags("</span>");
                highlightBuilder.fragmentSize(100);
                sourceBuilder.highlighter(highlightBuilder);
            }
        }

        // 手机号
        if (!ObjectUtils.isEmpty(mobileObj)) {
            boolQuery.must(QueryBuilders.prefixQuery("mobile", (String) mobileObj));
        }

        // 开始时间
        if (!ObjectUtils.isEmpty(startTimeObj)) {
            boolQuery.must(QueryBuilders.rangeQuery("sendTimeMillis")
                    .gte(Long.parseLong(startTimeObj.toString())));
        }

        // 结束时间
        if (!ObjectUtils.isEmpty(stopTimeObj)) {
            boolQuery.must(QueryBuilders.rangeQuery("sendTimeMillis")
                    .lte(Long.parseLong(stopTimeObj.toString())));
        }

        // 客户id
        if (clientIDList != null) {
            boolQuery.must(QueryBuilders.termsQuery("clientId", clientIDList.toArray(new Long[] {})));
        }

        return boolQuery;
    }

    private String listToDateString(List sendTime) {
        if (sendTime == null || sendTime.size() < 6) {
            return "";
        }
        try {
            Object year = sendTime.get(0);
            Object month = sendTime.get(1);
            Object day = sendTime.get(2);
            Object hour = sendTime.get(3);
            Object minute = sendTime.get(4);
            Object second = sendTime.get(5);

            return String.format("%s-%02d-%02d %02d:%02d:%02d",
                    year,
                    Integer.parseInt(month.toString()),
                    Integer.parseInt(day.toString()),
                    Integer.parseInt(hour.toString()),
                    Integer.parseInt(minute.toString()),
                    Integer.parseInt(second.toString()));
        } catch (Exception e) {
            log.error("解析日期列表出错: " + sendTime, e);
            return "";
        }
    }
}