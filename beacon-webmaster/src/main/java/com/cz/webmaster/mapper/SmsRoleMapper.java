package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.SmsRole;
import com.cz.webmaster.entity.SmsRoleExample;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SmsRoleMapper {
    long countByExample(SmsRoleExample example);

    int deleteByExample(SmsRoleExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(SmsRole row);

    int insertSelective(SmsRole row);

    List<SmsRole> selectByExample(SmsRoleExample example);

    SmsRole selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("row") SmsRole row, @Param("example") SmsRoleExample example);

    int updateByExample(@Param("row") SmsRole row, @Param("example") SmsRoleExample example);

    int updateByPrimaryKeySelective(SmsRole row);

    int updateByPrimaryKey(SmsRole row);

    @Select("select \n" +
            "\tname\n" +
            "from \n" +
            "\tsms_role sr\n" +
            "inner join \n" +
            "\tsms_user_role sur\n" +
            "on\n" +
            "\tsr.id = sur.role_id\n" +
            "where \n" +
            "  sur.user_id = #{userId}")
    Set<String> findRoleNameByUserId(@Param("userId") Integer userId);

    @Select("select menu_id from sms_role_menu where role_id = #{roleId}")
    List<Integer> findMenuIdsByRoleId(@Param("roleId") Integer roleId);

    @Delete("delete from sms_role_menu where role_id = #{roleId}")
    int deleteRoleMenuByRoleId(@Param("roleId") Integer roleId);

    @Insert({"<script>",
            "insert into sms_role_menu (role_id, menu_id) values ",
            "<foreach collection='menuIds' item='menuId' separator=','>",
            "(#{roleId}, #{menuId})",
            "</foreach>",
            "</script>"})
    int insertRoleMenus(@Param("roleId") Integer roleId, @Param("menuIds") List<Integer> menuIds);
}