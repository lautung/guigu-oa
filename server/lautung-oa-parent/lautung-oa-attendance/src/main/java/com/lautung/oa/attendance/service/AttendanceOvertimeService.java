package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.attendance.AttendanceOvertime;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceOvertimeSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeVo;

/**
 * 加班单（#282，ADR 0073；#294 薄壳，ADR 0075）：办事侧只读本人——列表/详情/草稿保存
 * 修改删除/提交。结束必须晚于开始且同一天内，小时数服务端计算；允许休息日加班，不与
 * 打卡交叉校验。草稿 CAS、提交序列、同键重放与终态回写委托业务单据生命周期 module。
 */
public interface AttendanceOvertimeService extends IService<AttendanceOvertime> {

    IPage<AttendanceOvertimeVo> page(Page<AttendanceOvertimeVo> page, Long applicantId,
                                     AttendanceApplyQueryVo query);

    AttendanceOvertimeVo detail(Long applicantId, Long id);

    AttendanceOvertimeVo saveDraft(Long applicantId, AttendanceOvertimeSaveRequest request);

    AttendanceOvertimeVo updateDraft(Long applicantId, AttendanceOvertimeUpdateRequest request);

    void removeDraft(Long applicantId, Long id);

    /** 提交（生命周期 module：校验 → 状态 CAS → 起审批）；同键重放返回同一条审批。 */
    AttendanceOvertimeVo submit(Long applicantId, Long id, String idempotencyKey);
}
