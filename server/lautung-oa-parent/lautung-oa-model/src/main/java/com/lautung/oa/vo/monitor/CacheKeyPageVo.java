package com.lautung.oa.vo.monitor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 缓存列表的一页（#237，ADR 0060）：SCAN 结果内存排序后按游标窗口切页；
 * {@code nextCursor} 为 0 表示已经翻到末尾。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheKeyPageVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<CacheKeyVo> keys;

    private long nextCursor;
}
