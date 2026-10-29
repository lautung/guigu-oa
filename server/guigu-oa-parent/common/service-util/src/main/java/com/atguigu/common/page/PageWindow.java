package com.atguigu.common.page;

import com.atguigu.common.config.exception.GuiguException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 服务端分页窗口：页码从 1 开始，每页最多 {@value #MAX_LIMIT} 条。
 * 越界直接拒绝，不静默截断，避免一次请求把整表拉出来。
 */
public final class PageWindow {

    public static final long MAX_LIMIT = 500;

    private PageWindow() {
    }

    public static <T> Page<T> of(Long page, Long limit) {
        if (page == null || page < 1) {
            throw new GuiguException(201, "页码必须大于等于 1");
        }
        if (limit == null || limit < 1 || limit > MAX_LIMIT) {
            throw new GuiguException(201, "每页记录数必须在 1 到 " + MAX_LIMIT + " 之间");
        }
        return new Page<>(page, limit);
    }
}
