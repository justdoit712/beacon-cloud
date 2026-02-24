package com.cz.webmaster.service;

import com.cz.webmaster.entity.ClientBusiness;

import java.util.List;

/**
 * @author cz
 * @description
 */
public interface ClientBusinessService {
    /**
     * 查询全部客户信息
     * @return
     */
    List<ClientBusiness> findAll();

    /**
     * 根据用户id查询客户信息
     * @param userId
     * @return
     */
    List<ClientBusiness> findByUserId(Integer userId);

    /**
     * 根据关键字分页查询
     *
     * @param keyword 关键字
     * @return 列表
     */
    List<ClientBusiness> findByKeyword(String keyword);

    /**
     * 统计数量
     *
     * @param keyword 关键字
     * @return 数量
     */
    long countByKeyword(String keyword);

    /**
     * 根据id查询
     *
     * @param id 主键
     * @return 实体
     */
    ClientBusiness findById(Long id);

    /**
     * 保存
     *
     * @param clientBusiness 实体
     * @return 是否成功
     */
    boolean save(ClientBusiness clientBusiness);

    /**
     * 更新
     *
     * @param clientBusiness 实体
     * @return 是否成功
     */
    boolean update(ClientBusiness clientBusiness);

    /**
     * 批量删除（逻辑删除）
     *
     * @param ids id集合
     * @return 是否成功
     */
    boolean deleteBatch(List<Long> ids);
}
