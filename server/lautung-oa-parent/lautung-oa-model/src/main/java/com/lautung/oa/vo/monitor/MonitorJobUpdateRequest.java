package com.lautung.oa.vo.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务编辑请求（ADR 0060，#238）：cron / 启停 / 并发 / 备注，null 字段不覆盖；
 * id 走路径。cron 的表达式合法性（{@code CronExpression}）与任务行存在性在 service 判，
 * 这里只钉静态形状（长度与取值范围）。
 */
@Data
public class MonitorJobUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Spring 6 字段 cron（秒 分 时 日 月 周）。 */
    @Size(max = 50, message = "cron 不能超过 50 个字符")
    private String cron;

    /** 0 停用 / 1 启用。 */
    @Min(value = 0, message = "enabled 只允许 0 或 1")
    @Max(value = 1, message = "enabled 只允许 0 或 1")
    private Integer enabled;

    /** 0 禁止并发（默认）/ 1 允许并发。 */
    @Min(value = 0, message = "concurrency 只允许 0 或 1")
    @Max(value = 1, message = "concurrency 只允许 0 或 1")
    private Integer concurrency;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
