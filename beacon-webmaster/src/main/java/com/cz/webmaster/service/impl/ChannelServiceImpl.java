package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.entity.Channel;
import com.cz.webmaster.mapper.ChannelMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.ChannelService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class ChannelServiceImpl implements ChannelService {

    @Autowired
    private ChannelMapper channelMapper;
    @Autowired
    private CacheSyncService cacheSyncService;
    @Autowired
    private CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    @Override
    public List<Channel> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return channelMapper.findListByPage(query, offset, limit);
    }

    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return channelMapper.countByKeyword(query);
    }

    @Override
    public Channel findById(Long id) {
        if (id == null) {
            return null;
        }
        return channelMapper.findById(id);
    }

    @Override
    public List<Channel> findAllActive() {
        return channelMapper.findAllActive();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Channel channel) {
        if (channel == null
                || !StringUtils.hasText(channel.getChannelName())
                || channel.getChannelType() == null
                || !StringUtils.hasText(channel.getChannelArea())
                || channel.getChannelPrice() == null
                || channel.getProtocolType() == null) {
            return false;
        }
        if (channel.getId() == null) {
            channel.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        channel.setCreated(now);
        channel.setUpdated(now);
        if (channel.getIsDelete() == null) {
            channel.setIsDelete((byte) 0);
        }
        if (channel.getIsAvailable() == null) {
            channel.setIsAvailable((byte) 0);
        }

        boolean saved = channelMapper.insertSelective(channel) > 0;
        if (!saved) {
            return false;
        }
        Channel latest = channelMapper.findById(channel.getId());
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CHANNEL, latest != null ? latest : channel),
                CacheDomainRegistry.CHANNEL,
                "upsert",
                safeEntityId(channel.getId())
        );
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Channel channel) {
        if (channel == null || channel.getId() == null) {
            return false;
        }
        channel.setUpdated(new Date());
        boolean updated = channelMapper.updateById(channel) > 0;
        if (!updated) {
            return false;
        }
        Channel latest = channelMapper.findById(channel.getId());
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CHANNEL, latest != null ? latest : channel),
                CacheDomainRegistry.CHANNEL,
                "upsert",
                safeEntityId(channel.getId())
        );
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        boolean deleted = channelMapper.deleteBatch(ids, new Date(), updateId) > 0;
        if (!deleted) {
            return false;
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                    () -> cacheSyncService.syncDelete(CacheDomainRegistry.CHANNEL, id),
                    CacheDomainRegistry.CHANNEL,
                    "delete",
                    safeEntityId(id)
            );
        }
        return true;
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}

