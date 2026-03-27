package com.cz.search.service.impl;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.SearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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

    private static ElasticsearchServiceImpl buildService(RestHighLevelClient client, RabbitTemplate rabbitTemplate) {
        ElasticsearchServiceImpl service = new ElasticsearchServiceImpl();
        ReflectionTestUtils.setField(service, "restHighLevelClient", client);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbitTemplate);
        return service;
    }
}
