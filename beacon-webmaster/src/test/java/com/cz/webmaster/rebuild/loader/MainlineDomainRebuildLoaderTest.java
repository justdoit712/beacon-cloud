package com.cz.webmaster.rebuild.loader;

import com.cz.webmaster.entity.Channel;
import com.cz.webmaster.entity.ClientBalance;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.MobileTransfer;
import com.cz.webmaster.mapper.ChannelMapper;
import com.cz.webmaster.mapper.ClientBalanceMapper;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.mapper.ClientChannelMapper;
import com.cz.webmaster.mapper.MobileTransferMapper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MainlineDomainRebuildLoaderTest {

    @Test
    public void shouldLoadActiveClientBusinessSnapshot() {
        ClientBusinessMapper mapper = Mockito.mock(ClientBusinessMapper.class);
        ClientBusinessDomainRebuildLoader loader = new ClientBusinessDomainRebuildLoader(mapper);

        ClientBusiness first = new ClientBusiness();
        first.setId(1001L);
        first.setApikey("ak_1001");
        ClientBusiness second = new ClientBusiness();
        second.setId(1002L);
        second.setApikey("ak_1002");

        when(mapper.selectByExample(Mockito.any())).thenReturn(Arrays.asList(first, second));

        List<Object> snapshot = loader.loadSnapshot();

        Assert.assertEquals(2, snapshot.size());
        Assert.assertSame(first, snapshot.get(0));
        Assert.assertSame(second, snapshot.get(1));

        ArgumentCaptor<com.cz.webmaster.entity.ClientBusinessExample> captor =
                ArgumentCaptor.forClass(com.cz.webmaster.entity.ClientBusinessExample.class);
        verify(mapper, times(1)).selectByExample(captor.capture());
        Assert.assertEquals("id asc", captor.getValue().getOrderByClause());
        Assert.assertTrue(captor.getValue().getOredCriteria().get(0).isValid());
    }

    @Test
    public void shouldLoadActiveChannelSnapshot() {
        ChannelMapper mapper = Mockito.mock(ChannelMapper.class);
        ChannelDomainRebuildLoader loader = new ChannelDomainRebuildLoader(mapper);

        Channel first = new Channel();
        first.setId(2001L);
        Channel second = new Channel();
        second.setId(2002L);

        when(mapper.findAllActive()).thenReturn(Arrays.asList(first, second));

        List<Object> snapshot = loader.loadSnapshot();

        Assert.assertEquals(2, snapshot.size());
        Assert.assertSame(first, snapshot.get(0));
        Assert.assertSame(second, snapshot.get(1));
        verify(mapper, times(1)).findAllActive();
    }

    @Test
    public void shouldLoadActiveClientBalanceSnapshot() {
        ClientBalanceMapper mapper = Mockito.mock(ClientBalanceMapper.class);
        ClientBalanceDomainRebuildLoader loader = new ClientBalanceDomainRebuildLoader(mapper);

        ClientBalance first = new ClientBalance();
        first.setId(5001L);
        first.setClientId(1001L);
        first.setBalance(200L);
        ClientBalance second = new ClientBalance();
        second.setId(5002L);
        second.setClientId(1002L);
        second.setBalance(350L);

        when(mapper.selectAllActive()).thenReturn(Arrays.asList(first, second));

        List<Object> snapshot = loader.loadSnapshot();

        Assert.assertEquals(2, snapshot.size());
        Assert.assertSame(first, snapshot.get(0));
        Assert.assertSame(second, snapshot.get(1));
        verify(mapper, times(1)).selectAllActive();
    }

    @Test
    public void shouldGroupClientChannelSnapshotByClientId() {
        ClientChannelMapper mapper = Mockito.mock(ClientChannelMapper.class);
        ClientChannelDomainRebuildLoader loader = new ClientChannelDomainRebuildLoader(mapper);

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("clientId", 3001L);
        row1.put("channelId", 4001L);
        row1.put("clientChannelWeight", 100);
        row1.put("clientChannelNumber", "1069");
        row1.put("isAvailable", 0);

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("clientId", 3001L);
        row2.put("channelId", 4002L);
        row2.put("clientChannelWeight", 80);
        row2.put("clientChannelNumber", "1070");
        row2.put("isAvailable", 1);

        Map<String, Object> row3 = new LinkedHashMap<>();
        row3.put("clientId", 3002L);
        row3.put("channelId", 5001L);
        row3.put("clientChannelWeight", 60);
        row3.put("clientChannelNumber", "2088");
        row3.put("isAvailable", 1);

        when(mapper.findActiveClientIds()).thenReturn(Arrays.asList(3001L, 3002L));
        when(mapper.findRouteMembersByClientIds(eq(Arrays.asList(3001L, 3002L))))
                .thenReturn(Arrays.asList(row1, row2, row3));

        List<Object> snapshot = loader.loadSnapshot();

        Assert.assertEquals(2, snapshot.size());
        Assert.assertTrue(snapshot.get(0) instanceof Map);
        Assert.assertTrue(snapshot.get(1) instanceof Map);

        Map<String, Object> firstPayload = (Map<String, Object>) snapshot.get(0);
        Map<String, Object> secondPayload = (Map<String, Object>) snapshot.get(1);

        Assert.assertEquals(3001L, firstPayload.get("clientId"));
        Assert.assertEquals(3002L, secondPayload.get("clientId"));
        Assert.assertTrue(firstPayload.get("members") instanceof List);
        Assert.assertTrue(secondPayload.get("members") instanceof List);

        List<Map<String, Object>> firstMembers = (List<Map<String, Object>>) firstPayload.get("members");
        List<Map<String, Object>> secondMembers = (List<Map<String, Object>>) secondPayload.get("members");

        Assert.assertEquals(2, firstMembers.size());
        Assert.assertEquals(1, secondMembers.size());
        Assert.assertFalse(firstMembers.get(0).containsKey("clientId"));
        Assert.assertEquals(4001L, firstMembers.get(0).get("channelId"));
        Assert.assertEquals(5001L, secondMembers.get(0).get("channelId"));
    }

    @Test
    public void shouldReturnEmptyClientChannelSnapshotWhenNoActiveClientId() {
        ClientChannelMapper mapper = Mockito.mock(ClientChannelMapper.class);
        ClientChannelDomainRebuildLoader loader = new ClientChannelDomainRebuildLoader(mapper);

        when(mapper.findActiveClientIds()).thenReturn(Collections.emptyList());

        List<Object> snapshot = loader.loadSnapshot();

        Assert.assertTrue(snapshot.isEmpty());
        verify(mapper, times(1)).findActiveClientIds();
        verify(mapper, times(0)).findRouteMembersByClientIds(Mockito.anyList());
    }

    @Test
    public void shouldLoadTransferSnapshotAsMobileValuePayload() {
        MobileTransferMapper mapper = Mockito.mock(MobileTransferMapper.class);
        TransferDomainRebuildLoader loader = new TransferDomainRebuildLoader(mapper);

        MobileTransfer valid = new MobileTransfer();
        valid.setId(9001L);
        valid.setTransferNumber(" 13800000000 ");
        valid.setNowIsp(2);

        MobileTransfer missingMobile = new MobileTransfer();
        missingMobile.setId(9002L);
        missingMobile.setNowIsp(3);

        MobileTransfer missingValue = new MobileTransfer();
        missingValue.setId(9003L);
        missingValue.setTransferNumber("13900000000");

        when(mapper.findAllActive()).thenReturn(Arrays.asList(valid, missingMobile, missingValue));

        List<Object> snapshot = loader.loadSnapshot();

        Assert.assertEquals(1, snapshot.size());
        Assert.assertTrue(snapshot.get(0) instanceof Map);
        Map<String, Object> payload = (Map<String, Object>) snapshot.get(0);
        Assert.assertEquals("13800000000", payload.get("mobile"));
        Assert.assertEquals("2", payload.get("value"));
        verify(mapper, times(1)).findAllActive();
    }
}
