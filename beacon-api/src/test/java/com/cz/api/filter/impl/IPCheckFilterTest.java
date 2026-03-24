package com.cz.api.filter.impl;

import com.cz.api.client.BeaconCacheClient;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import com.cz.common.model.StandardSubmit;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.when;

public class IPCheckFilterTest {

    @Test
    public void shouldParseCommaSeparatedWhiteListAndPassWhenMatched() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        IPCheckFilter filter = new IPCheckFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");
        submit.setRealIp("127.0.0.1");

        when(cacheClient.hgetString("client_business:ak_001", "ipAddress"))
                .thenReturn("22.220.124.110,127.0.0.1");

        filter.check(submit);

        Assert.assertEquals(Arrays.asList("22.220.124.110", "127.0.0.1"), submit.getIp());
    }

    @Test
    public void shouldPassWhenWhiteListBlank() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        IPCheckFilter filter = new IPCheckFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");
        submit.setRealIp("127.0.0.1");

        when(cacheClient.hgetString("client_business:ak_001", "ipAddress"))
                .thenReturn("   ");

        filter.check(submit);

        Assert.assertEquals(Collections.emptyList(), submit.getIp());
    }

    @Test
    public void shouldRejectWhenRealIpNotInWhiteList() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        IPCheckFilter filter = new IPCheckFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");
        submit.setRealIp("127.0.0.1");

        when(cacheClient.hgetString("client_business:ak_001", "ipAddress"))
                .thenReturn("22.220.124.110,10.0.0.1");

        try {
            filter.check(submit);
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertEquals(ExceptionEnums.IP_NOT_WHITE.getCode(), ex.getCode());
        }
    }
}
