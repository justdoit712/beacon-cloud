package com.cz.webmaster.service;

import com.cz.webmaster.entity.SmsUser;

import java.util.List;

/**
 * 用户信息的Service
 * @author cz
 * @description
 */
public interface SmsUserService {


    /**
     * 根据用户名查询用户信息
     * @param username  用户名
     * @return
     */
    SmsUser findByUsername(String username);

    /**
     * 分页场景查询用户
     *
     * @param keyword 关键字
     * @return 用户列表
     */
    List<SmsUser> findByKeyword(String keyword);

    /**
     * 统计用户条数
     *
     * @param keyword 关键字
     * @return 条数
     */
    long countByKeyword(String keyword);

    /**
     * 根据id查询用户
     *
     * @param id 用户id
     * @return 用户信息
     */
    SmsUser findById(Integer id);

    /**
     * 保存用户
     *
     * @param user 用户
     * @return 是否成功
     */
    boolean save(SmsUser user);

    /**
     * 更新用户
     *
     * @param user 用户
     * @return 是否成功
     */
    boolean update(SmsUser user);

    /**
     * 删除用户
     *
     * @param ids 用户id集合
     * @return 是否成功
     */
    boolean deleteBatch(List<Integer> ids);

    /**
     * 修改密码
     *
     * @param userId      用户id
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean updatePassword(Integer userId, String oldPassword, String newPassword);

}
