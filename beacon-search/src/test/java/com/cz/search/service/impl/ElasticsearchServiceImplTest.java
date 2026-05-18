package com.cz.search.service.impl;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.SearchException;
import com.cz.search.utils.SearchUtils;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class ElasticsearchServiceImplTest {

    @Test
    public void shouldIndexSuccessfullyWhenEsReturnsCreated() throws Exception {
        RestHighLevelClient client = Mockito.mock(RestHighLevelClient.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        ElasticsearchServiceImpl service = buildService(client, rabbitTemplate);

        IndexResponse response = Mockito.mock(IndexResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(client.index(any(IndexRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(response);

        service.index("sms_submit_log_2026", "3", "{\"clientId\":3}");

        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        Mockito.verify(client).index(requestCaptor.capture(), eq(RequestOptions.DEFAULT));
        IndexRequest request = requestCaptor.getValue();
        Assert.assertEquals("sms_submit_log_2026", request.index());
        Assert.assertEquals("3", request.id());
    }

    @Test
    public void shouldThrowSearchExceptionWhenEsReturnsUpdatedForIndex() throws Exception {
        RestHighLevelClient client = Mockito.mock(RestHighLevelClient.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        ElasticsearchServiceImpl service = buildService(client, rabbitTemplate);

        IndexResponse response = Mockito.mock(IndexResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.UPDATED);
        when(client.index(any(IndexRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(response);

        try {
            service.index("sms_submit_log_2026", "3", "{\"clientId\":3}");
            Assert.fail("expected SearchException");
        } catch (SearchException ex) {
            Assert.assertEquals(ExceptionEnums.SEARCH_INDEX_ERROR.getCode(), ex.getCode());
        }
    }

    @Test
    public void shouldBuildQueryWithHighlightAndScalarClientId() {
        ElasticsearchServiceImpl service = buildService(
                Mockito.mock(RestHighLevelClient.class),
                Mockito.mock(RabbitTemplate.class)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("content", "hello");
        params.put("mobile", "138");
        params.put("starttime", "1000");
        params.put("stoptime", "2000");
        params.put("clientID", "1001");

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQuery = ReflectionTestUtils.invokeMethod(service, "buildBoolQuery", params, sourceBuilder);

        Assert.assertNotNull(boolQuery);
        Assert.assertEquals(5, boolQuery.must().size());
        Assert.assertNotNull(sourceBuilder.highlighter());
    }

    @Test
    public void shouldBuildQueryWithClientIdListWithoutHighlight() {
        ElasticsearchServiceImpl service = buildService(
                Mockito.mock(RestHighLevelClient.class),
                Mockito.mock(RabbitTemplate.class)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("clientID", Arrays.asList(1001L, 1002L));

        BoolQueryBuilder boolQuery = ReflectionTestUtils.invokeMethod(service, "buildBoolQuery", params, null);

        Assert.assertNotNull(boolQuery);
        Assert.assertEquals(1, boolQuery.must().size());
    }

    @Test
    public void shouldBuildQueryWithIntegerClientIdListWithoutArrayStoreException() {
        ElasticsearchServiceImpl service = buildService(
                Mockito.mock(RestHighLevelClient.class),
                Mockito.mock(RabbitTemplate.class)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("clientID", Arrays.asList(1001, 1002));

        BoolQueryBuilder boolQuery = ReflectionTestUtils.invokeMethod(service, "buildBoolQuery", params, null);

        Assert.assertNotNull(boolQuery);
        Assert.assertEquals(1, boolQuery.must().size());
    }

    @Test
    public void shouldBuildQueryWithNumericScalarClientId() {
        ElasticsearchServiceImpl service = buildService(
                Mockito.mock(RestHighLevelClient.class),
                Mockito.mock(RabbitTemplate.class)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("clientID", 1001);

        BoolQueryBuilder boolQuery = ReflectionTestUtils.invokeMethod(service, "buildBoolQuery", params, null);

        Assert.assertNotNull(boolQuery);
        Assert.assertEquals(1, boolQuery.must().size());
    }

    @Test
    public void shouldBuildQueryWithoutClientConditionWhenClientIdMissing() {
        ElasticsearchServiceImpl service = buildService(
                Mockito.mock(RestHighLevelClient.class),
                Mockito.mock(RabbitTemplate.class)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("content", "keyword");

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQuery = ReflectionTestUtils.invokeMethod(service, "buildBoolQuery", params, sourceBuilder);

        Assert.assertNotNull(boolQuery);
        Assert.assertEquals(1, boolQuery.must().size());
        Assert.assertNotNull(sourceBuilder.highlighter());
    }

    @Test
    public void shouldUseSingleValidIndexForListQuery() throws Exception {
        RestHighLevelClient client = Mockito.mock(RestHighLevelClient.class);
        ElasticsearchServiceImpl service = buildService(client, Mockito.mock(RabbitTemplate.class));

        SearchResponse searchResponse = Mockito.mock(SearchResponse.class);
        SearchHits searchHits = Mockito.mock(SearchHits.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(searchHits.getTotalHits()).thenReturn(new TotalHits(0, TotalHits.Relation.EQUAL_TO));
        when(searchHits.getHits()).thenReturn(new org.elasticsearch.search.SearchHit[0]);
        when(client.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(searchResponse);

        service.findSmsByParameters(new HashMap<String, Object>());

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        Mockito.verify(client).search(requestCaptor.capture(), eq(RequestOptions.DEFAULT));
        SearchRequest request = requestCaptor.getValue();
        Assert.assertArrayEquals(new String[] {SearchUtils.getCurrYearIndex()}, request.indices());
    }

    @Test
    public void shouldUseSingleValidIndexForCountQuery() throws Exception {
        RestHighLevelClient client = Mockito.mock(RestHighLevelClient.class);
        ElasticsearchServiceImpl service = buildService(client, Mockito.mock(RabbitTemplate.class));

        SearchResponse searchResponse = Mockito.mock(SearchResponse.class);
        Aggregations aggregations = Mockito.mock(Aggregations.class);
        when(searchResponse.getAggregations()).thenReturn(aggregations);
        when(client.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(searchResponse);

        service.countSmsState(new HashMap<String, Object>());

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        Mockito.verify(client).search(requestCaptor.capture(), eq(RequestOptions.DEFAULT));
        SearchRequest request = requestCaptor.getValue();
        Assert.assertArrayEquals(new String[] {SearchUtils.getCurrYearIndex()}, request.indices());
    }

    @Test
    public void shouldResolveSendTimeFromMillisFirst() {
        ElasticsearchServiceImpl service = buildService(
                Mockito.mock(RestHighLevelClient.class),
                Mockito.mock(RabbitTemplate.class)
        );

        LocalDateTime sendTime = LocalDateTime.of(2026, 4, 23, 14, 2, 47);
        long epochMillis = sendTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Map<String, Object> row = new HashMap<>();
        row.put("sendTime", Arrays.asList(2026, 4, 23, 14, 2, 40));
        row.put("sendTimeMillis", epochMillis);

        String sendTimeStr = ReflectionTestUtils.invokeMethod(service, "resolveSendTimeStr", row);

        Assert.assertEquals("2026-04-23 14:02:47", sendTimeStr);
    }

    @Test
    public void shouldFallbackToLegacySendTimeListWhenMillisMissing() {
        ElasticsearchServiceImpl service = buildService(
                Mockito.mock(RestHighLevelClient.class),
                Mockito.mock(RabbitTemplate.class)
        );

        Map<String, Object> row = new HashMap<>();
        row.put("sendTime", Arrays.asList(2026, 4, 23, 14, 2, 47));

        String sendTimeStr = ReflectionTestUtils.invokeMethod(service, "resolveSendTimeStr", row);

        Assert.assertEquals("2026-04-23 14:02:47", sendTimeStr);
    }

    private static ElasticsearchServiceImpl buildService(RestHighLevelClient client, RabbitTemplate rabbitTemplate) {
        ElasticsearchServiceImpl service = new ElasticsearchServiceImpl();
        ReflectionTestUtils.setField(service, "restHighLevelClient", client);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbitTemplate);
        return service;
    }
}
