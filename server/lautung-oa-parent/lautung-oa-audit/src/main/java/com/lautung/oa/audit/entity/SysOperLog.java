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
 * 操作日志（ADR 0054）。审计表是历史事实：只有 {@code create_time}，没有 {@code update_time}
 * 与 {@code is_deleted}，物理删除（清空必须是真的清空），所以刻意不继承 {@code BaseEntity}。
 *
 * <p>{@code title}/{@code oper_name}/{@code dept_name} 都是写入时快照，读侧不 join。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILURE = 1;

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("title")
    private String title;

    /** {@code BusinessType.getCode()}：稳定小整数，不是枚举序号。 */
    @TableField("business_type")
    private Integer businessType;

    /** {@code 类#方法}，取声明类型的全限定名。 */
    @TableField("method")
    private String method;

    @TableField("request_method")
    private String requestMethod;

    @TableField("oper_id")
    private Long operId;

    @TableField("oper_name")
    private String operName;

    @TableField("dept_id")
    private Long deptId;

    @TableField("dept_name")
    private String deptName;

    @TableField("oper_url")
    private String operUrl;

    @TableField("oper_ip")
    private String operIp;

    @TableField("oper_param")
    private String operParam;

    @TableField("result_code")
    private Integer resultCode;

    @TableField("result_message")
    private String resultMessage;

    /** 0 成功 / 1 失败。 */
    @TableField("status")
    private Integer status;

    @TableField("error_msg")
    private String errorMsg;

    /** 方法执行耗时（毫秒），不含落库本身。 */
    @TableField("cost_time")
    private Long costTime;

    /**
     * 公共字段（ADR 0031）。非 {@code BaseEntity} 实体也能被
     * {@code CommonFieldsMetaObjectHandler} 按字段名填充，前提是声明 {@code fill}。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
}
