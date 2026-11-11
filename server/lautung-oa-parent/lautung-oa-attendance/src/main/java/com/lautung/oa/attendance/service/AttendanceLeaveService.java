package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.attendance.AttendanceLeave;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveVo;
import com.lautung.oa.vo.attendance.AttendanceMyLeaveTypeVo;

import java.util.List;

/**
 * 请假单（#282，ADR 0073；#294 薄壳，ADR 0075）：办事侧只读本人——列表/详情/草稿保存
 * 修改删除/提交，类名与「后台假期类型」区分。提交由业务单据生命周期 module 编排：服务端
 * 定稿天数与小时数 + 校验 + 状态 CAS + 起审批，事务边界与同键重放归 module；审批终态
 * 回写由回调委托 module。
 */
public interface AttendanceLeaveService extends IService<AttendanceLeave> {

    IPage<AttendanceLeaveVo> page(Page<AttendanceLeaveVo> page, Long applicantId,
                                  AttendanceApplyQueryVo query);

    AttendanceLeaveVo detail(Long applicantId, Long id);

    AttendanceLeaveVo saveDraft(Long applicantId, AttendanceLeaveSaveRequest request);

    AttendanceLeaveVo updateDraft(Long applicantId, AttendanceLeaveUpdateRequest request);

    void removeDraft(Long applicantId, Long id);

    /** 提交（生命周期 module：校验定稿 → 状态 CAS → 起审批）；同键重放返回同一条审批。 */
    AttendanceLeaveVo submit(Long applicantId, Long id, String idempotencyKey);

    /** 本人可选的启用假期类型（需要余额的带可用余额）。 */
    List<AttendanceMyLeaveTypeVo> leaveTypes(Long applicantId);
}
