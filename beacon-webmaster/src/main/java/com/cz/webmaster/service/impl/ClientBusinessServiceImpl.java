package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.ClientBusinessExample;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.ClientBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

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

    @Override
    public List<ClientBusiness> findByKeyword(String keyword) {
        ClientBusinessExample example = buildExample(keyword);
        example.setOrderByClause("id desc");
        return clientBusinessMapper.selectByExample(example);
    }

    @Override
    public long countByKeyword(String keyword) {
        ClientBusinessExample example = buildExample(keyword);
        return clientBusinessMapper.countByExample(example);
    }

    @Override
    public ClientBusiness findById(Long id) {
        return clientBusinessMapper.selectByPrimaryKey(id);
    }

    /**
     * 新增客户业务配置
     * * @param clientBusiness 客户业务实体对象（由前端传入）
     * @return boolean true: 保存成功; false: 保存失败或参数为空
     */
    @Override
    public boolean save(ClientBusiness clientBusiness) {
        // 1. 基础防空校验：防止传入空对象导致后续的空指针异常 (NPE)
        if (clientBusiness == null) {
            return false;
        }

        // 2. 主键 ID 生成：如果前端未传递 ID，则使用 Hutool 的雪花算法生成分布式全局唯一 ID
        if (clientBusiness.getId() == null) {
            clientBusiness.setId(cn.hutool.core.util.IdUtil.getSnowflakeNextId());
        }

        // 3. 业务默认值填充：如果未指定过滤器策略，默认赋予“黑名单、敏感词、路由”三大基础校验策略
        if (!StringUtils.hasText(clientBusiness.getClientFilters())) {
            clientBusiness.setClientFilters("black,dirtyword,route");
        }

        // 4. 审计字段填充：统一设置创建时间和更新时间为当前系统时间
        Date now = new Date();
        clientBusiness.setCreated(now);
        clientBusiness.setUpdated(now);

        // 5. 逻辑删除标识：默认设置为 0（正常状态，未被删除）
        if (clientBusiness.getIsDelete() == null) {
            clientBusiness.setIsDelete((byte) 0);
        }

        // 6. 执行入库：使用 insertSelective（动态 SQL），只插入非空字段，返回影响行数大于 0 则代表成功
        return clientBusinessMapper.insertSelective(clientBusiness) > 0;
    }

    @Override
    public boolean update(ClientBusiness clientBusiness) {
        if (clientBusiness == null || clientBusiness.getId() == null) {
            return false;
        }
        clientBusiness.setUpdated(new Date());
        return clientBusinessMapper.updateByPrimaryKeySelective(clientBusiness) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        Date now = new Date();
        for (Long id : ids) {
            ClientBusiness cb = new ClientBusiness();
            cb.setId(id);
            cb.setIsDelete((byte) 1);
            cb.setUpdated(now);
            if (clientBusinessMapper.updateByPrimaryKeySelective(cb) <= 0) {
                return false;
            }
        }
        return true;
    }

    private ClientBusinessExample buildExample(String keyword) {
        ClientBusinessExample example = new ClientBusinessExample();
        ClientBusinessExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        if (StringUtils.hasText(keyword)) {
            criteria.andCorpnameLike("%" + keyword.trim() + "%");
        }
        return example;
    }
}
