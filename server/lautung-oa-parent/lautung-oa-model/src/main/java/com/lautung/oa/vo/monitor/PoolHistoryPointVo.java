package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;

/**
 * 连接池历史的一个采样点（#258）：采样时刻的活跃/空闲/总数/等待线程数。
 * 只存在进程内环（120 点），重启清零，不落库、不做时序存储。
 */
@Data
public class PoolHistoryPointVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 采样时刻（epoch 毫秒）。 */
    private Long timestamp;

    private Integer activeConnections;

    private Integer idleConnections;

    private Integer totalConnections;

    private Integer threadsAwaiting;
}
