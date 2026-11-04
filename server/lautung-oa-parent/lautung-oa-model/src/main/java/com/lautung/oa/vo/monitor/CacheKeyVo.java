package com.lautung.oa.vo.monitor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 缓存列表的一行（#237，ADR 0060）：键名与剩余 TTL（秒；{@code -1} 表示无过期）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheKeyVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String key;

    private Long ttl;
}
