package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.attendance.AttendanceRepairUserVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考勤成员候选的用户小读 Mapper（#278，ADR 0073）：只读 sys_user 的启用行，
 * 成员候选不依赖 auth 模块与用户管理页权限（跨表小读例外，ADR 0050 同族）。
 *
 * <p>#281 增补卡页的成员候选：按 ADR 0053/0057 的五档数据范围过滤
 * （部门列 {@code sys_user.dept_id} 当前 + 本人列 {@code sys_user.id}），
 * 空集得零行，不是全部。
 */
public interface AttendanceUserMapper extends BaseMapper<SysUser> {

    List<AttendanceRepairUserVo> selectScopedUsers(@Param("name") String name,
                                                   @Param("userId") Long userId,
                                                   @Param("scope") DataScope scope);
}
