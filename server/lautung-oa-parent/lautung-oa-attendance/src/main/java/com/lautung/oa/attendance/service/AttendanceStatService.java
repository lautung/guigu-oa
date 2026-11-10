package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceMonthVo;
import com.lautung.oa.vo.attendance.AttendanceStatVo;

/**
 * 月度考勤统计（#283，ADR 0073 §统计口径）：读时聚合，不落统计表。
 *
 * <p>汇总逐日状态全部来自 {@link AttendanceDayStatusService}（唯一推导），
 * 加班时长来自审批通过的加班单定稿小时数。行集合与数据范围由查询 SQL 收口，
 * 下钻复用同一个行集判定，不能靠 userId 绕过列表范围。
 */
public interface AttendanceStatService {

    /** 按月统计（按数据范围），{@code month} 缺省取注入时钟的当前月。 */
    IPage<AttendanceStatVo> page(Page<AttendanceStatVo> page, String month, DataScope scope);

    /** 点人下钻：先按列表同一行集合 + 数据范围校验可见性，再复用逐日推导。 */
    AttendanceMonthVo month(Long userId, String month, DataScope scope);
}
