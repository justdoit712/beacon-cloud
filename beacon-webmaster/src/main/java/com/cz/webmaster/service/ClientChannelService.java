package com.cz.webmaster.service;

import com.cz.webmaster.entity.ClientChannel;
import java.util.List;

public interface ClientChannelService {


    List<ClientChannel> findListByPage(String keyword, int offset, int limit);

    long countByKeyword(String keyword);

    ClientChannel findById(Long id);

    boolean save(ClientChannel clientChannel);

    boolean update(ClientChannel clientChannel);

    boolean deleteBatch(List<Long> ids, Long updateId);
}