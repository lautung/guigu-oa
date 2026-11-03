package com.lautung.oa.vo.system;

/**
 * /info 下发的后台菜单条目（ADR 0058）。path 是解析后的绝对路径，parentPath 只用于展示层级。
 */
public record BackofficeMenuVo(
        String path,
        String parentPath,
        String title,
        Integer type,
        boolean hidden,
        Integer sort,
        String icon) {
}
