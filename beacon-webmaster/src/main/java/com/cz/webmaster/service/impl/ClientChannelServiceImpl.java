package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.entity.ClientChannel;
import com.cz.webmaster.mapper.ClientChannelMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.ClientChannelService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ClientChannelServiceImpl implements ClientChannelService {

    @Autowired
    private ClientChannelMapper clientChannelMapper;
    @Autowired
    private CacheSyncService cacheSyncService;
    @Autowired
    private CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    @Override
    public List<ClientChannel> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return clientChannelMapper.findListByPage(query, offset, limit);
    }

    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return clientChannelMapper.countByKeyword(query);
    }

    @Override
    public ClientChannel findById(Long id) {
        if (id == null) {
            return null;
        }
        return clientChannelMapper.findById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ClientChannel clientChannel) {
        if (clientChannel == null
                || clientChannel.getClientId() == null
                || clientChannel.getChannelId() == null
                || !StringUtils.hasText(clientChannel.getExtendNumber())
                || clientChannel.getPrice() == null) {
            return false;
        }
        if (clientChannel.getId() == null) {
            clientChannel.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        clientChannel.setCreated(now);
        clientChannel.setUpdated(now);
        if (clientChannel.getIsDelete() == null) {
            clientChannel.setIsDelete((byte) 0);
        }
        boolean saved = clientChannelMapper.insertSelective(clientChannel) > 0;
        if (!saved) {
            return false;
        }
        scheduleClientChannelRebuild(clientChannel.getClientId(), "upsert", safeEntityId(clientChannel.getId()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ClientChannel clientChannel) {
        if (clientChannel == null || clientChannel.getId() == null) {
            return false;
        }

        ClientChannel before = clientChannelMapper.findById(clientChannel.getId());
        clientChannel.setUpdated(new Date());
        boolean updated = clientChannelMapper.updateById(clientChannel) > 0;
        if (!updated) {
            return false;
        }

        ClientChannel latest = clientChannelMapper.findById(clientChannel.getId());
        Set<Long> affectedClientIds = new LinkedHashSet<>();
        addClientId(affectedClientIds, before == null ? null : before.getClientId());
        addClientId(affectedClientIds, latest == null ? null : latest.getClientId());
        for (Long clientId : affectedClientIds) {
            scheduleClientChannelRebuild(clientId, "upsert", safeEntityId(clientChannel.getId()));
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }

        List<ClientChannel> beforeList = clientChannelMapper.findByIds(ids);
        Set<Long> affectedClientIds = new LinkedHashSet<>();
        if (beforeList != null) {
            for (ClientChannel item : beforeList) {
                addClientId(affectedClientIds, item == null ? null : item.getClientId());
            }
        }

        boolean deleted = clientChannelMapper.deleteBatch(ids, new Date(), updateId) > 0;
        if (!deleted) {
            return false;
        }
        for (Long clientId : affectedClientIds) {
            scheduleClientChannelRebuild(clientId, "delete", safeEntityId(clientId));
        }
        return true;
    }

    private void scheduleClientChannelRebuild(Long clientId, String operation, String entityId) {
        if (clientId == null || clientId <= 0) {
            return;
        }
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_CHANNEL, buildClientChannelPayload(clientId)),
                CacheDomainRegistry.CLIENT_CHANNEL,
                operation,
                entityId
        );
    }

    private Map<String, Object> buildClientChannelPayload(Long clientId) {
        List<Map<String, Object>> members = clientChannelMapper.findRouteMembersByClientIds(Collections.singletonList(clientId));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", clientId);
        payload.put("members", members == null ? new ArrayList<>() : members);
        return payload;
    }

    private void addClientId(Set<Long> clientIds, Long clientId) {
        if (clientId != null && clientId > 0) {
            clientIds.add(clientId);
        }
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}

