package com.lautung.oa.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.model.monitor.SysJobLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 定时任务执行记录 Mapper（ADR 0060，#238）。
 *
 * <p>保留策略的 SQL 是手写的：MySQL 不允许 DELETE 的子查询直接引用目标表，
 * 也不允许 IN 子查询带 LIMIT，所以按「派生表再包一层」的形状写
 * （{@code DELETE … WHERE id NOT IN (SELECT id FROM (SELECT … ORDER BY id DESC LIMIT n) t)}）。
 */
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {

    /** 每任务只留最近 {@code keep} 条（按 id 降序），更老的物理删除。 */
    @Delete("""
            DELETE FROM sys_job_log
            WHERE job_id = #{jobId}
              AND id NOT IN (
                SELECT id FROM (
                  SELECT id FROM sys_job_log WHERE job_id = #{jobId} ORDER BY id DESC LIMIT #{keep}
                ) t
              )
            """)
    int trimPerJob(@Param("jobId") Long jobId, @Param("keep") int keep);

    /** 全局只留最近 {@code keep} 条，更老的物理删除。 */
    @Delete("""
            DELETE FROM sys_job_log
            WHERE id NOT IN (
              SELECT id FROM (
                SELECT id FROM sys_job_log ORDER BY id DESC LIMIT #{keep}
              ) t
            )
            """)
    int trimGlobal(@Param("keep") int keep);
}
