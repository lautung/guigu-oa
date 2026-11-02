package com.lautung.oa.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.audit.entity.SysOperLog;

/**
 * 操作日志的写入入口：只用 {@code insert}，查询面归 #214。
 */
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
