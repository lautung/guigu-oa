package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.model.system.SysUser;

/**
 * 考勤成员候选的用户小读 Mapper（#278，ADR 0073）：只读 sys_user 的启用行，
 * 成员候选不依赖 auth 模块与用户管理页权限（跨表小读例外，ADR 0050 同族）。
 */
public interface AttendanceUserMapper extends BaseMapper<SysUser> {
}
