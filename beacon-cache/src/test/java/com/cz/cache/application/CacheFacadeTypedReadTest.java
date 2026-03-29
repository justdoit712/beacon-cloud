package com.cz.cache.application;

import com.cz.cache.redis.LocalRedisClient;
import com.cz.cache.redis.NamespaceKeyResolver;
import com.cz.cache.redis.RedisScanService;
import com.cz.cache.security.CacheNamespaceProperties;
import com.cz.cache.security.CacheSecurityProperties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CacheFacadeTypedReadTest {

    private LocalRedisClient redisClient;
    private CacheFacade cacheFacade;

    @Before
    public void setUp() {
        redisClient = mock(LocalRedisClient.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisScanService redisScanService = mock(RedisScanService.class);
        CacheSecurityProperties cacheSecurityProperties = new CacheSecurityProperties();
        CacheNamespaceProperties namespaceProperties = new CacheNamespaceProperties();
        NamespaceKeyResolver namespaceKeyResolver = mock(NamespaceKeyResolver.class);
        when(namespaceKeyResolver.toPhysicalKey(anyString())).thenAnswer(invocation -> "ns:" + invocation.getArgument(0));

        cacheFacade = new CacheFacade(
                redisClient,
                redisTemplate,
                redisScanService,
                cacheSecurityProperties,
                namespaceProperties,
                namespaceKeyResolver
        );
    }

    @Test
    public void shouldConvertHashAllToStringMap() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "alice");
        source.put("balance", 12);
        source.put("enabled", true);
        when(redisClient.hGetAll("ns:client:1")).thenReturn(source);

        Map<String, String> result = cacheFacade.hGetAllString("client:1");

        Assert.assertEquals("alice", result.get("name"));
        Assert.assertEquals("12", result.get("balance"));
        Assert.assertEquals("true", result.get("enabled"));
    }

    @Test
    public void shouldParseIntegerFromString() {
        when(redisClient.hGet("ns:client:1", "balance")).thenReturn("20");

        Integer result = cacheFacade.hGetInteger("client:1", "balance");

        Assert.assertEquals(Integer.valueOf(20), result);
    }

    @Test
    public void shouldRejectInvalidIntegerType() {
        when(redisClient.hGet("ns:client:1", "balance")).thenReturn("20x");

        try {
            cacheFacade.hGetInteger("client:1", "balance");
            Assert.fail("expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            Assert.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    @Test
    public void shouldRejectDecimalForInteger() {
        when(redisClient.hGet("ns:client:1", "balance")).thenReturn(20.5d);

        try {
            cacheFacade.hGetInteger("client:1", "balance");
            Assert.fail("expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            Assert.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    @Test
    public void shouldConvertSetMembersToString() {
        Set<Object> source = new LinkedHashSet<>(Arrays.asList("a", 1, true));
        when(redisClient.sMembers("ns:set:1")).thenReturn(source);

        Set<String> result = cacheFacade.sMembersString("set:1");

        Assert.assertEquals(new LinkedHashSet<>(Arrays.asList("a", "1", "true")), result);
    }

    @Test
    public void shouldConvertSetMembersToMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("channelId", 10);
        map.put("weight", 50);
        Set<Object> source = new LinkedHashSet<>(Collections.singletonList(map));
        when(redisClient.sMembers("ns:set:map")).thenReturn(source);

        Set<Map<String, Object>> result = cacheFacade.sMembersMap("set:map");

        Assert.assertEquals(1, result.size());
        Assert.assertEquals(map, result.iterator().next());
    }

    @Test
    public void shouldRejectNonMapSetMember() {
        Set<Object> source = new LinkedHashSet<>(Collections.singletonList("bad"));
        when(redisClient.sMembers("ns:set:map")).thenReturn(source);

        try {
            cacheFacade.sMembersMap("set:map");
            Assert.fail("expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            Assert.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }
}
