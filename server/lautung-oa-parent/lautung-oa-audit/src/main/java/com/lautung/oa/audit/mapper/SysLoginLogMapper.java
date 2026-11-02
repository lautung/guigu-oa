package com.lautung.oa.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.audit.entity.SysLoginLog;

/**
 * 登录日志的写入入口：本票只用 {@code insert}，查询面归 #216。
 */
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
