package com.lautung.oa.attendance.service;

import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceDayStatusVo;
import com.lautung.oa.vo.attendance.AttendanceRepairRequest;
import com.lautung.oa.vo.attendance.AttendanceRepairUserVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 后台补卡（#281，ADR 0073）：在指定成员 + 日期上追加来源「补卡」的流水行。
 *
 * <p>append-only：每个非空槽位追加一行 source=2，原网页行保留；重复补卡追加新行，
 * 推导取最新。补卡不走审批，操作日志由控制器 {@code @OperLog} 采集。
 * 成员候选与写入都按五档数据范围（ADR 0053/0057）校验，不能靠 ID 绕过。
 */
public interface AttendanceRepairService {

    /** 可见成员候选；{@code name} 为姓名/用户名包含匹配。 */
    List<AttendanceRepairUserVo> users(String name, DataScope scope);

    /** 指定成员的单日推导；成员不在数据范围内 201「成员不在数据范围内」。 */
    AttendanceDayStatusVo day(Long userId, LocalDate date, DataScope scope);

    /** 追加补卡行并返回刷新后的当日状态。 */
    AttendanceDayStatusVo repair(AttendanceRepairRequest request, Long operatorUserId, DataScope scope);
}
