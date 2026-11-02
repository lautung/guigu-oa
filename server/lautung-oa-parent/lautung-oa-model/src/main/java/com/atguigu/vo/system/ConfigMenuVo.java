package com.atguigu.vo.system;

/**
 * /info 下发的配置菜单条目。path 是解析后的绝对路径，parentPath 只用于展示层级。
 */
public record ConfigMenuVo(
        String path,
        String parentPath,
        String title,
        Integer type,
        boolean hidden,
        Integer sort) {
}
