package com.lautung.oa.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.model.monitor.SysJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 定时任务行 Mapper（ADR 0060，#238）。
 *
 * <p>登记要能看见软删行：删掉的行下次启动会被补回（恢复而不是插新行，避免撞
 * {@code uk_sys_job_bean_name}），所以按 bean 名的查行走手写 SQL，不吃 {@code @TableLogic}。
 */
public interface SysJobMapper extends BaseMapper<SysJob> {

    @Select("""
            SELECT id, name, bean_name, cron, enabled, concurrency, remark,
                   create_time, update_time, is_deleted
            FROM sys_job
            WHERE bean_name = #{beanName}
            """)
    SysJob selectAnyByBeanName(@Param("beanName") String beanName);

    /** 登记补回软删行：只翻存活标记，cron / 启停 / 备注都不覆盖。 */
    @Update("""
            UPDATE sys_job
            SET is_deleted = 0, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND is_deleted = 1
            """)
    int resurrect(@Param("id") Long id);
}
