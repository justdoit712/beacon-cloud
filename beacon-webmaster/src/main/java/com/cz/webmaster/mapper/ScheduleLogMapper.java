package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.ScheduleLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ScheduleLogMapper {

    @Select({
            "<script>",
            "select count(1) from schedule_log",
            "<if test='keyword != null and keyword != \"\"'>",
            "where bean_name like concat('%',#{keyword},'%')",
            "or method_name like concat('%',#{keyword},'%')",
            "or ifnull(params,'') like concat('%',#{keyword},'%')",
            "or ifnull(error,'') like concat('%',#{keyword},'%')",
            "</if>",
            "</script>"
    })
    long count(@Param("keyword") String keyword);

    @Select({
            "<script>",
            "select log_id, job_id, bean_name, method_name, params, status, times, error, create_time",
            "from schedule_log",
            "<if test='keyword != null and keyword != \"\"'>",
            "where bean_name like concat('%',#{keyword},'%')",
            "or method_name like concat('%',#{keyword},'%')",
            "or ifnull(params,'') like concat('%',#{keyword},'%')",
            "or ifnull(error,'') like concat('%',#{keyword},'%')",
            "</if>",
            "order by log_id desc",
            "limit #{offset}, #{limit}",
            "</script>"
    })
    List<ScheduleLog> list(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    @Insert("insert into schedule_log(log_id, job_id, bean_name, method_name, params, status, times, error, create_time) " +
            "values(#{logId}, #{jobId}, #{beanName}, #{methodName}, #{params}, #{status}, #{times}, #{error}, #{createTime})")
    int insert(ScheduleLog log);

    @Delete({
            "<script>",
            "delete from schedule_log where log_id in",
            "<foreach collection='logIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int deleteBatch(@Param("logIds") List<Long> logIds);
}
