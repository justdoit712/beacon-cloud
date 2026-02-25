package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.entity.Channel;
import com.cz.webmaster.mapper.ChannelMapper;
import com.cz.webmaster.service.ChannelService;
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
        return channelMapper.insertSelective(channel) > 0;
    }

    @Override
    public boolean update(Channel channel) {
        if (channel == null || channel.getId() == null) {
            return false;
        }
        channel.setUpdated(new Date());
        return channelMapper.updateById(channel) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return channelMapper.deleteBatch(ids, new Date(), updateId) > 0;
    }
}