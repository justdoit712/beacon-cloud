package com.cz.webmaster.service;

import com.cz.webmaster.entity.SmsMenu;

import java.util.List;
import java.util.Map;

/**
 * 菜单Service
 * @author cz
 * @description
 */
public interface SmsMenuService {
    /**
     * 根据用户id查询用户菜单信息
     * @param id 用户Id
     * @return
     */
    List<Map<String, Object>> findUserMenu(Integer id);

    /**
     * 分页查询菜单
     *
     * @param keyword 关键字
     * @return 菜单列表
     */
    List<SmsMenu> findByKeyword(String keyword);

    /**
     * 统计菜单数量
     *
     * @param keyword 关键字
     * @return 条数
     */
    long countByKeyword(String keyword);

    /**
     * 根据id查询菜单
     *
     * @param id 菜单id
     * @return 菜单
     */
    SmsMenu findById(Integer id);

    /**
     * 查询全部菜单
     *
     * @return 菜单列表
     */
    List<SmsMenu> findAll();

    /**
     * 保存菜单
     *
     * @param menu 菜单
     * @return 是否成功
     */
    boolean save(SmsMenu menu);

    /**
     * 更新菜单
     *
     * @param menu 菜单
     * @return 是否成功
     */
    boolean update(SmsMenu menu);

    /**
     * 删除菜单
     *
     * @param ids 菜单id集合
     * @return 是否成功
     */
    boolean deleteBatch(List<Integer> ids);
}
