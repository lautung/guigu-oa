package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.attendance.AttendanceLeaveLedger;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceLeaveBalanceVo;
import com.lautung.oa.vo.attendance.AttendanceLedgerQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLedgerVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 假期余额流水 Mapper（#280，ADR 0073）：后台发放流水列表 join 姓名/部门/类型/操作人，
 * 数据范围由查询参数 {@link DataScope} 落到 {@code sys_user.dept_id}（当前部门）与
 * {@code attendance_leave_ledger.user_id}（本人列）；本人余额按用户聚合账本与在途。
 *
 * <p>手写 SQL 的存活过滤显式带（ADR 0050）；类型 join 不按 status 过滤，
 * 停用类型的历史流水照读。结束日期由 service 转成次日零点开区间，避免
 * MySQL/H2 的日期加一天方言差异。
 */
public interface AttendanceLeaveLedgerMapper extends BaseMapper<AttendanceLeaveLedger> {

    IPage<AttendanceLedgerVo> selectLedgerPage(Page<AttendanceLedgerVo> page,
                                               @Param("query") AttendanceLedgerQueryVo query,
                                               @Param("beginTime") LocalDateTime beginTime,
                                               @Param("endExclusive") LocalDateTime endExclusive,
                                               @Param("scope") DataScope scope);

    List<AttendanceLeaveBalanceVo> selectBalances(@Param("userId") Long userId);
}
