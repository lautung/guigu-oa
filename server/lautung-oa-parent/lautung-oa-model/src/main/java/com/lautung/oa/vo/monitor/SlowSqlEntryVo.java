package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;

/**
 * 数据监控页的一行慢 SQL（ADR 0060）：字段集合与缓冲条目一一对应——
 * {@code timestamp / statementId / elapsedMs / rows / sql}，<b>没有参数字段</b>；
 * SQL 已在缓冲侧截断到 1024 字符。
 */
@Data
public class SlowSqlEntryVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录时刻（epoch 毫秒），客户端负责格式化。 */
    private Long timestamp;

    private String statementId;

    private Long elapsedMs;

    private Long rows;

    private String sql;
}
