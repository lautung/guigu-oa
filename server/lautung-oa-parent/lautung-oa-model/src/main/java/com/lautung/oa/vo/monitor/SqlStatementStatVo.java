package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;

/**
 * SQL 统计的一行（#258）：按 {@code statementId} 聚合慢 SQL 内存环内的条目——
 * 次数、平均耗时、最大耗时与最近一条时间。数据来源只有慢 SQL 环，
 * <b>不是全量 SQL</b>（响应里的 note 明示这一点）。
 */
@Data
public class SqlStatementStatVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String statementId;

    private Long count;

    /** 组内平均耗时（毫秒），保留一位小数。 */
    private Double avgElapsedMs;

    private Long maxElapsedMs;

    /** 组内最近一条的 epoch 毫秒。 */
    private Long lastTimestamp;
}
