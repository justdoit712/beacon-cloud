package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.ScheduleJob;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ScheduleJobMapper {

    @Select({
            "<script>",
            "select count(1) from schedule_job",
            "<if test='keyword != null and keyword != \"\"'>",
            "where bean_name like concat('%',#{keyword},'%')",
            "or method_name like concat('%',#{keyword},'%')",
            "or ifnull(params,'') like concat('%',#{keyword},'%')",
            "or ifnull(remark,'') like concat('%',#{keyword},'%')",
            "</if>",
            "</script>"
    })
    long count(@Param("keyword") String keyword);

    @Select({
            "<script>",
            "select job_id, bean_name, method_name, params, cron_expression, remark, status, create_time, update_time",
            "from schedule_job",
            "<if test='keyword != null and keyword != \"\"'>",
            "where bean_name like concat('%',#{keyword},'%')",
            "or method_name like concat('%',#{keyword},'%')",
            "or ifnull(params,'') like concat('%',#{keyword},'%')",
            "or ifnull(remark,'') like concat('%',#{keyword},'%')",
            "</if>",
            "order by job_id desc",
            "limit #{offset}, #{limit}",
            "</script>"
    })
    List<ScheduleJob> list(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    @Select("select job_id, bean_name, method_name, params, cron_expression, remark, status, create_time, update_time from schedule_job where job_id = #{jobId}")
    ScheduleJob selectById(@Param("jobId") Long jobId);

    @Select("select job_id, bean_name, method_name, params, cron_expression, remark, status, create_time, update_time from schedule_job")
    List<ScheduleJob> selectAll();

    @Insert("insert into schedule_job(job_id, bean_name, method_name, params, cron_expression, remark, status, create_time, update_time) " +
            "values(#{jobId}, #{beanName}, #{methodName}, #{params}, #{cronExpression}, #{remark}, #{status}, #{createTime}, #{updateTime})")
    int insert(ScheduleJob job);

    @Update("update schedule_job set bean_name=#{beanName}, method_name=#{methodName}, params=#{params}, cron_expression=#{cronExpression}, " +
            "remark=#{remark}, status=#{status}, update_time=#{updateTime} where job_id=#{jobId}")
    int updateById(ScheduleJob job);

    @Update({
            "<script>",
            "update schedule_job set status=#{status}, update_time=#{updateTime} where job_id in",
            "<foreach collection='jobIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int updateStatusBatch(@Param("jobIds") List<Long> jobIds,
                          @Param("status") Integer status,
                          @Param("updateTime") java.util.Date updateTime);

    @Delete({
            "<script>",
            "delete from schedule_job where job_id in",
            "<foreach collection='jobIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int deleteBatch(@Param("jobIds") List<Long> jobIds);
}
