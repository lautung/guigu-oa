package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 定时任务列表的一行（ADR 0060，#238）：表驱动任务行 + 可用性 + 最近一次执行摘要。
 *
 * <p>{@code available} 是读侧判存活：{@code bean_name} 找不到对应 {@code MonitorJob} bean
 * 的行标 false 且不调度（「异常」）；{@code last*} 是最近一次执行的摘要，
 * 没跑过时全部为空，完整记录走执行记录接口。
 */
@Data
public class MonitorJobVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String beanName;

    private String cron;

    private Integer enabled;

    private Integer concurrency;

    private String remark;

    private boolean available;

    /** 最近一次执行的 trigger_type（1 自动 / 2 手动），没跑过为空。 */
    private Integer lastTriggerType;

    /** 最近一次执行的状态（1 成功 / 2 失败 / 3 跳过），没跑过为空。 */
    private Integer lastStatus;

    private Date lastStartedAt;

    private Long lastDurationMs;

    private String lastMessage;
}
