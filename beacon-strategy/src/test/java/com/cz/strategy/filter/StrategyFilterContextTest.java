package com.cz.strategy.filter;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StrategyFilterContextTest {

    @Test
    public void shouldExpandLegacyBlackAliasAndKeepConfiguredOrder() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        StrategyFilter blackGlobal = Mockito.mock(StrategyFilter.class);
        StrategyFilter blackClient = Mockito.mock(StrategyFilter.class);
        StrategyFilter route = Mockito.mock(StrategyFilter.class);

        StrategyFilterContext context = new StrategyFilterContext();
        ReflectionTestUtils.setField(context, "cacheClient", cacheClient);

        Map<String, StrategyFilter> filters = new HashMap<>();
        filters.put("blackGlobal", blackGlobal);
        filters.put("blackClient", blackClient);
        filters.put("route", route);
        ReflectionTestUtils.setField(context, "stringStrategyFilterMap", filters);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");

        when(cacheClient.hget(CacheKeyConstants.CLIENT_BUSINESS + "ak_001", "clientFilters"))
                .thenReturn("black,route");

        context.strategy(submit);

        InOrder inOrder = Mockito.inOrder(blackGlobal, blackClient, route);
        inOrder.verify(blackGlobal).strategy(submit);
        inOrder.verify(blackClient).strategy(submit);
        inOrder.verify(route).strategy(submit);
    }

    @Test
    public void shouldIgnoreBlankAndUnknownFiltersButStillRunKnownOnes() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        StrategyFilter route = Mockito.mock(StrategyFilter.class);

        StrategyFilterContext context = new StrategyFilterContext();
        ReflectionTestUtils.setField(context, "cacheClient", cacheClient);

        Map<String, StrategyFilter> filters = new HashMap<>();
        filters.put("route", route);
        ReflectionTestUtils.setField(context, "stringStrategyFilterMap", filters);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");

        when(cacheClient.hget(CacheKeyConstants.CLIENT_BUSINESS + "ak_001", "clientFilters"))
                .thenReturn("  route  , ,missing ");

        context.strategy(submit);

        verify(route).strategy(submit);
    }

    @Test
    public void shouldDoNothingWhenClientFiltersMissing() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        StrategyFilter route = Mockito.mock(StrategyFilter.class);

        StrategyFilterContext context = new StrategyFilterContext();
        ReflectionTestUtils.setField(context, "cacheClient", cacheClient);

        Map<String, StrategyFilter> filters = new HashMap<>();
        filters.put("route", route);
        ReflectionTestUtils.setField(context, "stringStrategyFilterMap", filters);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");

        when(cacheClient.hget(CacheKeyConstants.CLIENT_BUSINESS + "ak_001", "clientFilters"))
                .thenReturn(null);

        context.strategy(submit);

        verify(route, never()).strategy(submit);
    }
}
