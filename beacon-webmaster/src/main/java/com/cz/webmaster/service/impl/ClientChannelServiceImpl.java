package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.entity.ClientChannel;
import com.cz.webmaster.mapper.ClientChannelMapper;
import com.cz.webmaster.service.ClientChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class ClientChannelServiceImpl implements ClientChannelService {

    @Autowired
    private ClientChannelMapper clientChannelMapper;

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
        return clientChannelMapper.insertSelective(clientChannel) > 0;
    }

    @Override
    public boolean update(ClientChannel clientChannel) {
        if (clientChannel == null || clientChannel.getId() == null) {
            return false;
        }
        clientChannel.setUpdated(new Date());
        return clientChannelMapper.updateById(clientChannel) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return clientChannelMapper.deleteBatch(ids, new Date(), updateId) > 0;
    }
}