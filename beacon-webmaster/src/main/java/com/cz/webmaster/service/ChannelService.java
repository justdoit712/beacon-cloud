package com.cz.webmaster.service;

import com.cz.webmaster.entity.Channel;
import java.util.List;

public interface ChannelService {


    List<Channel> findListByPage(String keyword, int offset, int limit);

    long countByKeyword(String keyword);

    Channel findById(Long id);

    List<Channel> findAllActive();

    boolean save(Channel channel);

    boolean update(Channel channel);

    boolean deleteBatch(List<Long> ids, Long updateId);
}