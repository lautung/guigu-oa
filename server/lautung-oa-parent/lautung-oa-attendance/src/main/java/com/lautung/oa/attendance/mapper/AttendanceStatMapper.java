package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceStatVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 月度统计行集合 Mapper（#283，ADR 0073）。
 *
 * <p>行集合 = 组内启用账号 ∪ 当月有考勤流水（{@code attendance_punch}）的账号；
 * 停用账号只在有流水的月份出现。数据范围五档（ADR 0053/0057）在 SQL 里落成
 * 部门列 {@code sys_user.dept_id} 当前值 + 本人列 {@code sys_user.id}，空集
 * {@code 1 = 0} 得零行；手写 SQL 的存活过滤显式带（ADR 0050）。
 */
public interface AttendanceStatMapper extends BaseMapper<AttendancePunch> {

    IPage<AttendanceStatVo> selectStatPage(Page<AttendanceStatVo> page,
                                           @Param("beginDate") LocalDate beginDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("scope") DataScope scope);

    /** 下钻前的可见性校验：与列表同一行集合 + 同一数据范围，不能靠 userId 绕过。 */
    AttendanceStatVo selectScopedStatUser(@Param("beginDate") LocalDate beginDate,
                                          @Param("endDate") LocalDate endDate,
                                          @Param("userId") Long userId,
                                          @Param("scope") DataScope scope);
}
