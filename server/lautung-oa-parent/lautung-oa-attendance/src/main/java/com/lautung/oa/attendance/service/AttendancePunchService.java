package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendancePunchQueryVo;
import com.lautung.oa.vo.attendance.AttendancePunchVo;
import com.lautung.oa.vo.attendance.AttendanceTodayVo;

/**
 * 打卡（#279，ADR 0073）：办事侧只读本人（今日状态 + 上班/下班打卡），
 * 后台侧按数据范围读打卡记录。日状态读时推导，不落日报。
 */
public interface AttendancePunchService extends IService<AttendancePunch> {

    AttendanceTodayVo today(Long userId);

    AttendanceTodayVo punch(Long userId, int slot);

    IPage<AttendancePunchVo> page(Page<AttendancePunchVo> page,
                                 AttendancePunchQueryVo query,
                                 DataScope scope);
}
