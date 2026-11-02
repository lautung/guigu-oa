package com.lautung.oa.vo.system;

import lombok.Data;

import java.io.Serializable;

/**
 * 操作日志列表的过滤条件（ADR 0054，#214）。
 *
 * <p>时间范围沿用现有列表的字符串绑定（{@code create_time} 是 DATETIME，交给 JDBC 解析）；
 * 数据范围不在这里，由 {@code DataScopeProvider} 按当前登录人解析后作为查询参数传入。
 */
@Data
public class SysOperLogQueryVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作人姓名，模糊匹配 {@code oper_name}。 */
    private String operName;

    /** 操作名称，模糊匹配 {@code title}。 */
    private String title;

    /** {@code BusinessType.getCode()}。 */
    private Integer businessType;

    /** 0 成功 / 1 失败。 */
    private Integer status;

    private String createTimeBegin;

    private String createTimeEnd;
}
