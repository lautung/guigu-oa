package com.lautung.oa.model.monitor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 定时任务执行记录（ADR 0060，#238）：自动与手动共用一条执行路径写入的执行事实。
 *
 * <p>历史事实表：无公共字段、无 {@code is_deleted}，由保留策略在写入路径做物理删除
 * （每任务留最近 20 条、全局留最近 500 条）。{@code job_name} 是写入时快照，
 * 读侧不 join {@code sys_job}。毫秒精度落在 {@code duration_ms}。
 */
@Data
@TableName("sys_job_log")
public class SysJobLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** trigger_type：1 自动调度 / 2 手动执行。 */
    public static final int TRIGGER_AUTO = 1;
    public static final int TRIGGER_MANUAL = 2;

    /** status：1 成功 / 2 失败 / 3 跳过（并发禁止且上一轮未完）。 */
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILURE = 2;
    public static final int STATUS_SKIPPED = 3;

    /** 失败信息的截断上限（message 列宽）。 */
    public static final int MESSAGE_MAX_LENGTH = 1000;

    /** 保留策略（写入路径就地清，ADR 0060）：每任务留最近 20 条、全局留最近 500 条。 */
    public static final int RETENTION_PER_JOB = 20;
    public static final int RETENTION_GLOBAL = 500;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("job_id")
    private Long jobId;

    @TableField("job_name")
    private String jobName;

    @TableField("trigger_type")
    private Integer triggerType;

    @TableField("status")
    private Integer status;

    @TableField("message")
    private String message;

    @TableField("started_at")
    private Date startedAt;

    @TableField("finished_at")
    private Date finishedAt;

    @TableField("duration_ms")
    private Long durationMs;

    /** message 列宽截断：失败信息超长只留前 1000 字符。 */
    public static String truncateMessage(String message) {
        if (message == null || message.length() <= MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, MESSAGE_MAX_LENGTH);
    }
}
