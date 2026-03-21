package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.ClientChannel;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * {@code client_channel} 表的数据访问接口。
 *
 * <p>该 Mapper 负责客户与通道绑定关系的基础查询、写入、更新和逻辑删除，
 * 同时提供构建 {@code client_channel} 集合型缓存所需的全量快照查询能力。</p>
 */
public interface ClientChannelMapper {

    /**
     * 按分页条件查询客户通道列表。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @param offset 分页起始偏移量
     * @param limit 每页大小
     * @return 客户通道列表
     */
    List<ClientChannel> findListByPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 按关键字统计客户通道数量。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 匹配的客户通道数量
     */
    long countByKeyword(@Param("keyword") String keyword);

    /**
     * 按主键查询客户通道详情。
     *
     * @param id 客户通道 id
     * @return 客户通道对象；未命中时返回 {@code null}
     */
    ClientChannel findById(@Param("id") Long id);

    /**
     * 按主键集合批量查询客户通道详情。
     *
     * @param ids 客户通道 id 集合
     * @return 客户通道列表
     */
    List<ClientChannel> findByIds(@Param("ids") List<Long> ids);

    /**
     * 查询当前存在有效绑定关系的客户 id 列表。
     *
     * <p>该查询用于全量重建 {@code client_channel} 域时确定需要输出的
     * {@code client_channel:{clientId}} key 集合。</p>
     *
     * @return 当前存在有效通道绑定的客户 id 列表
     */
    List<Long> findActiveClientIds();

    /**
     * 按客户 id 集合查询客户通道路由成员的全量快照。
     *
     * <p>该方法不是返回 {@link ClientChannel} 实体列表，而是返回用于构造
     * {@code client_channel:{clientId}} 集合缓存的成员快照数据。
     * 典型字段包括通道 id、通道号、权重、可用状态等。</p>
     *
     * <p>该查询用于集合型缓存重建场景：当某个客户的通道绑定关系发生变化时，
     * 需要重新拉取该客户当前全部有效成员，再整组回写缓存，而不是只更新单条成员。</p>
     *
     * @param clientIds 客户 id 集合
     * @return 按客户维度构建缓存集合所需的全量成员快照列表
     */
    List<Map<String, Object>> findRouteMembersByClientIds(@Param("clientIds") List<Long> clientIds);

    /**
     * 新增客户通道绑定关系。
     *
     * @param clientChannel 客户通道对象
     * @return 受影响行数
     */
    int insertSelective(ClientChannel clientChannel);

    /**
     * 更新客户通道绑定关系。
     *
     * @param clientChannel 客户通道对象
     * @return 受影响行数
     */
    int updateById(ClientChannel clientChannel);

    /**
     * 批量逻辑删除客户通道绑定关系。
     *
     * @param ids 需要删除的客户通道 id 集合
     * @param updated 统一写入的更新时间
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return 受影响行数
     */
    int deleteBatch(@Param("ids") List<Long> ids, @Param("updated") Date updated, @Param("updateId") Long updateId);
}
