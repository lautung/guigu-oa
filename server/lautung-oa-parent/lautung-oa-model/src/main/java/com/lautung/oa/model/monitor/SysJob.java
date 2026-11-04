package com.lautung.oa.model.monitor;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务行（ADR 0060，#238）：任务行由启动登记产生（缺行插入、有行不覆盖），
 * 页面不提供新增与删除——删掉的行下次启动会被补回，只能改 cron / 启停 / 并发 / 备注。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends BaseEntity {

    /** enabled：0 停用（登记缺行的默认值）/ 1 启用。 */
    public static final int ENABLED_OFF = 0;
    public static final int ENABLED_ON = 1;

    /** concurrency：0 禁止（默认，上一轮未完则本次记「跳过」）/ 1 允许。 */
    public static final int CONCURRENCY_FORBID = 0;
    public static final int CONCURRENCY_ALLOW = 1;

    @TableField("name")
    private String name;

    /** 实现了 MonitorJob 接口的 Spring bean 名，唯一。 */
    @TableField("bean_name")
    private String beanName;

    /** Spring 6 字段 cron（秒 分 时 日 月 周），写入时经 CronExpression 校验。 */
    @TableField("cron")
    private String cron;

    @TableField("enabled")
    private Integer enabled;

    @TableField("concurrency")
    private Integer concurrency;

    @TableField("remark")
    private String remark;
}
