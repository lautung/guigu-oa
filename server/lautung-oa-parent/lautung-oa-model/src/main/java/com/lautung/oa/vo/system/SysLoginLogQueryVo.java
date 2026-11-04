package com.lautung.oa.vo.system;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录日志列表的过滤条件（ADR 0054，#216）。
 *
 * <p>时间范围沿用现有列表的字符串绑定（{@code create_time} 是 DATETIME，交给 JDBC 解析）；
 * 数据范围不在这里，由 {@code DataScopeProvider} 按当前登录人解析后作为查询参数传入。
 */
@Data
public class SysLoginLogQueryVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提交的账号，模糊匹配 {@code username}（存原文，不打码）。 */
    private String username;

    /** 客户端地址，模糊匹配 {@code ip}。 */
    private String ip;

    /** 0 成功 / 1 失败 / 2 锁定 / 3 解锁。 */
    private Integer status;

    /** 事件类型：{@code login} / {@code register}（#253，ADR 0062）；为空不过滤。 */
    private String event;

    private String createTimeBegin;

    private String createTimeEnd;
}
