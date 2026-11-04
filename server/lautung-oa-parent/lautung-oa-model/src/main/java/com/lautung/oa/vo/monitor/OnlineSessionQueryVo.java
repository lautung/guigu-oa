package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;

/**
 * 在线用户列表的过滤条件（ADR 0060）：用户名包含匹配与登录时间闭区间。
 *
 * <p>时间沿用登录日志页的 {@code yyyy-MM-dd HH:mm:ss} 字符串形状；由读侧解析成会话时间
 * 值类型后比较，不把解析规则散到控制器。
 */
@Data
public class OnlineSessionQueryVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名包含匹配（大小写不敏感）。 */
    private String username;

    /** 登录时间下界，格式 {@code yyyy-MM-dd HH:mm:ss}。 */
    private String beginTime;

    /** 登录时间上界，格式 {@code yyyy-MM-dd HH:mm:ss}。 */
    private String endTime;
}
