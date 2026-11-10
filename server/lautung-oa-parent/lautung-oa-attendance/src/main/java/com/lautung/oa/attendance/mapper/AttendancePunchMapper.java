package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceLeaveSpanVo;
import com.lautung.oa.vo.attendance.AttendancePunchQueryVo;
import com.lautung.oa.vo.attendance.AttendancePunchVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 打卡事件流水 Mapper（#279，ADR 0073）：后台记录页 join 姓名/部门/补卡操作人，
 * 数据范围由查询参数 {@link DataScope} 落到 {@code sys_user.dept_id}（当前部门）与
 * {@code attendance_punch.user_id}（本人列）。手写 SQL 的存活过滤显式带（ADR 0050），
 * 本查询不读审批行，因此不需要 approvalRowLiveness。
 *
 * <p>#281 增逐日推导用的请假区间小读：推导 service 与打卡记录共用
 * {@link BaseMapper} 已注册的这条 Mapper，不为一条只读 SQL 再加扫描配置。
 */
public interface AttendancePunchMapper extends BaseMapper<AttendancePunch> {

    IPage<AttendancePunchVo> selectPunchPage(Page<AttendancePunchVo> page,
                                             @Param("query") AttendancePunchQueryVo query,
                                             @Param("scope") DataScope scope);

    List<AttendanceLeaveSpanVo> selectApprovedLeaves(@Param("userId") Long userId,
                                                     @Param("beginDate") LocalDate beginDate,
                                                     @Param("endDate") LocalDate endDate);
}
