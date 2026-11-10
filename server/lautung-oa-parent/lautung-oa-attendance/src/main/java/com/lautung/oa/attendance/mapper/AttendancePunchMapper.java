package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendancePunchQueryVo;
import com.lautung.oa.vo.attendance.AttendancePunchVo;
import org.apache.ibatis.annotations.Param;

/**
 * 打卡事件流水 Mapper（#279，ADR 0073）：后台记录页 join 姓名/部门/补卡操作人，
 * 数据范围由查询参数 {@link DataScope} 落到 {@code sys_user.dept_id}（当前部门）与
 * {@code attendance_punch.user_id}（本人列）。手写 SQL 的存活过滤显式带（ADR 0050），
 * 本查询不读审批行，因此不需要 approvalRowLiveness。
 */
public interface AttendancePunchMapper extends BaseMapper<AttendancePunch> {

    IPage<AttendancePunchVo> selectPunchPage(Page<AttendancePunchVo> page,
                                             @Param("query") AttendancePunchQueryVo query,
                                             @Param("scope") DataScope scope);
}
