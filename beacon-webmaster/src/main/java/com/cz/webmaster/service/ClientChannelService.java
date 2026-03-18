package com.cz.webmaster.service;

import com.cz.webmaster.entity.ClientChannel;

import java.util.List;

/**
 * 客户通道服务接口。
 *
 * <p>该接口负责客户与通道绑定关系的查询、创建、更新和逻辑删除操作。
 * 对于写操作，调用方只关心业务是否成功；缓存同步、事务提交后的处理
 * 由实现类统一负责。</p>
 */
public interface ClientChannelService {

    /**
     * 按分页条件查询客户通道列表。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @param offset 分页起始偏移量
     * @param limit 每页大小
     * @return 客户通道列表
     */
    List<ClientChannel> findListByPage(String keyword, int offset, int limit);

    /**
     * 按关键字统计客户通道数量。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 匹配的客户通道数量
     */
    long countByKeyword(String keyword);

    /**
     * 按主键查询客户通道详情。
     *
     * @param id 客户通道 id
     * @return 客户通道对象；未命中时返回 {@code null}
     */
    ClientChannel findById(Long id);

    /**
     * 新增客户通道绑定关系。
     *
     * @param clientChannel 客户通道对象
     * @return {@code true} 表示新增成功，{@code false} 表示新增失败
     */
    boolean save(ClientChannel clientChannel);

    /**
     * 更新客户通道绑定关系。
     *
     * @param clientChannel 客户通道对象
     * @return {@code true} 表示更新成功，{@code false} 表示更新失败
     */
    boolean update(ClientChannel clientChannel);

    /**
     * 批量逻辑删除客户通道绑定关系。
     *
     * @param ids 需要删除的客户通道 id 集合
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return {@code true} 表示删除成功，{@code false} 表示删除失败
     */
    boolean deleteBatch(List<Long> ids, Long updateId);
}
