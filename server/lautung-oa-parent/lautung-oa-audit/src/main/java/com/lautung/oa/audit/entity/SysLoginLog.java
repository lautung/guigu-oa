package com.lautung.oa.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 登录日志（ADR 0054）。和 {@code SysOperLog} 一样是历史事实：物理删除、没有
 * {@code is_deleted}/{@code update_time}，所以刻意不继承 {@code BaseEntity}；只有
 * {@code create_time} 由 {@code CommonFieldsMetaObjectHandler} 按字段名填充。
 *
 * <p>{@code status} 四值由 {@code LoginEventType} 提供：0 成功 / 1 失败 / 2 锁定 / 3 解锁。
 * {@code username} 存提交的原文（不做手机号打码），{@code user_id}/{@code dept_id} 是写入时
 * 按 username 解析的快照；{@code browser}/{@code os} 只存解析结果，不存 UA 原串。
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("user_id")
    private Long userId;

    @TableField("dept_id")
    private Long deptId;

    @TableField("ip")
    private String ip;

    @TableField("browser")
    private String browser;

    @TableField("os")
    private String os;

    /** 0 成功 / 1 失败 / 2 锁定 / 3 解锁。 */
    @TableField("status")
    private Integer status;

    @TableField("msg")
    private String msg;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
}
