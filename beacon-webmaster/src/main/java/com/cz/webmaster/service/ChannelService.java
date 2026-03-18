package com.cz.webmaster.service;

import com.cz.webmaster.entity.Channel;

import java.util.List;

/**
 * 通道服务接口。
 *
 * <p>该接口负责通道主数据的查询、创建、更新和删除操作。
 * 对于写操作，调用方只关心业务是否成功；缓存同步、事务提交后的处理
 * 由实现类统一负责。</p>
 */
public interface ChannelService {

    /**
     * 按分页条件查询通道列表。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @param offset 分页起始偏移量
     * @param limit 每页大小
     * @return 通道列表
     */
    List<Channel> findListByPage(String keyword, int offset, int limit);

    /**
     * 按关键字统计通道数量。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 匹配的通道数量
     */
    long countByKeyword(String keyword);

    /**
     * 按主键查询通道详情。
     *
     * @param id 通道 id
     * @return 通道对象；未命中时返回 {@code null}
     */
    Channel findById(Long id);

    /**
     * 查询全部有效通道。
     *
     * @return 有效通道列表
     */
    List<Channel> findAllActive();

    /**
     * 新增通道。
     *
     * @param channel 通道对象
     * @return {@code true} 表示新增成功，{@code false} 表示新增失败
     */
    boolean save(Channel channel);

    /**
     * 更新通道。
     *
     * @param channel 通道对象
     * @return {@code true} 表示更新成功，{@code false} 表示更新失败
     */
    boolean update(Channel channel);

    /**
     * 批量删除通道。
     *
     * @param ids 需要删除的通道 id 集合
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return {@code true} 表示删除成功，{@code false} 表示删除失败
     */
    boolean deleteBatch(List<Long> ids, Long updateId);
}
