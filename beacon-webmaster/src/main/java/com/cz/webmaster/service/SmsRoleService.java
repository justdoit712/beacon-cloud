package com.cz.webmaster.service;

import com.cz.webmaster.entity.SmsRole;

import java.util.List;
import java.util.Set;

/**
 * @author cz
 * @description
 */
public interface SmsRoleService {
    /**
     * 根据用户id，查询角色名称
     * @param userId
     * @return
     */
    Set<String> getRoleName(Integer userId);

    /**
     * 分页查询角色
     *
     * @param name   名称
     * @param status 状态
     * @return 角色列表
     */
    List<SmsRole> findByCondition(String name, String status);

    /**
     * 统计角色数量
     *
     * @param name   名称
     * @param status 状态
     * @return 数量
     */
    long countByCondition(String name, String status);

    /**
     * 根据id查询角色
     *
     * @param id 角色id
     * @return 角色
     */
    SmsRole findById(Integer id);

    /**
     * 保存角色
     *
     * @param role 角色
     * @return 是否成功
     */
    boolean save(SmsRole role);

    /**
     * 更新角色
     *
     * @param role 角色
     * @return 是否成功
     */
    boolean update(SmsRole role);

    /**
     * 删除角色
     *
     * @param ids 角色id集合
     * @return 是否成功
     */
    boolean deleteBatch(List<Integer> ids);

    /**
     * 根据角色id查询菜单id集合
     *
     * @param roleId 角色id
     * @return 菜单id集合
     */
    List<Integer> findMenuIdsByRoleId(Integer roleId);

    /**
     * 给角色分配菜单
     *
     * @param roleId  角色id
     * @param menuIds 菜单id集合
     * @return 是否成功
     */
    boolean assignMenu(Integer roleId, List<Integer> menuIds);
}
