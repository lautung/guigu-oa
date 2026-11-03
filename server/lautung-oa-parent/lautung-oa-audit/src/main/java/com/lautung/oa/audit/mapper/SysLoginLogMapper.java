package com.lautung.oa.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.audit.entity.SysLoginLog;

/**
 * 登录日志的读写入口：{@code insert} 归落库链路（#215），分页/删除/清空归查询面（#216）。
 * 表没有逻辑删除，{@code deleteByIds}/{@code delete(null)} 都是物理删除。
 */
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
