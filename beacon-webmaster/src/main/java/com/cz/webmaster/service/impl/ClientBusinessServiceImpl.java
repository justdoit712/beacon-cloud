package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.ClientBusinessExample;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.ClientBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author cz
 * @description
 */
@Service
public class ClientBusinessServiceImpl implements ClientBusinessService {

    @Autowired
    private ClientBusinessMapper clientBusinessMapper;

    @Override
    public List<ClientBusiness> findAll() {
        List<ClientBusiness> list = clientBusinessMapper.selectByExample(null);
        return list;
    }

    @Override
    public List<ClientBusiness> findByUserId(Integer userId) {
        ClientBusinessExample example = new ClientBusinessExample();
        example.createCriteria().andExtend1EqualTo(userId + "");
        List<ClientBusiness> list = clientBusinessMapper.selectByExample(example);
        return list;
    }
}
