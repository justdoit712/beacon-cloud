package com.cz.webmaster.service;

import com.cz.webmaster.entity.ClientBusiness;

import java.util.List;

/**
 * 客户业务服务接口。
 *
 * <p>该接口负责客户业务主数据的查询、创建、更新和逻辑删除操作。
 * 对于写操作，调用方只关心业务是否成功；缓存同步、事务提交后的处理
 * 由实现类统一负责。</p>
 */
public interface ClientBusinessService {

    /**
     * 查询全部客户业务信息。
     *
     * @return 客户业务列表
     */
    List<ClientBusiness> findAll();

    /**
     * 根据用户 id 查询其可见的客户业务信息。
     *
     * @param userId 用户 id
     * @return 客户业务列表
     */
    List<ClientBusiness> findByUserId(Integer userId);

    /**
     * 根据关键字查询客户业务列表。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 客户业务列表
     */
    List<ClientBusiness> findByKeyword(String keyword);

    /**
     * 根据关键字统计客户业务数量。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 匹配的客户业务数量
     */
    long countByKeyword(String keyword);

    /**
     * 根据主键查询客户业务详情。
     *
     * @param id 客户业务 id
     * @return 客户业务对象；未命中时返回 {@code null}
     */
    ClientBusiness findById(Long id);

    /**
     * 新增客户业务信息。
     *
     * @param clientBusiness 客户业务对象
     * @return {@code true} 表示新增成功，{@code false} 表示新增失败
     */
    boolean save(ClientBusiness clientBusiness);

    /**
     * 更新客户业务信息。
     *
     * @param clientBusiness 客户业务对象
     * @return {@code true} 表示更新成功，{@code false} 表示更新失败
     */
    boolean update(ClientBusiness clientBusiness);

    /**
     * 批量逻辑删除客户业务信息。
     *
     * @param ids 需要删除的客户业务 id 集合
     * @return {@code true} 表示删除成功，{@code false} 表示删除失败
     */
    boolean deleteBatch(List<Long> ids);
}
