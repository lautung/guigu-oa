package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.application.ApplyTexts;
import com.lautung.oa.attendance.application.AttendanceOvertimeDocumentAdapter;
import com.lautung.oa.attendance.application.DocumentLifecycle;
import com.lautung.oa.attendance.mapper.AttendanceOvertimeMapper;
import com.lautung.oa.attendance.service.AttendanceOvertimeService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceOvertime;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceOvertimeSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeVo;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/**
 * 加班单薄壳（#282，ADR 0073；#294 收成壳，ADR 0075）：列表/详情与本单据的草稿形状
 * 校验留在这里；草稿 CAS、提交序列、同键重放与终态回写全部委托
 * {@link DocumentLifecycle}，领域差异在 {@link AttendanceOvertimeDocumentAdapter}。
 * 结束必须晚于开始且同一天内，小时数服务端计算；允许休息日加班，不与打卡交叉校验。
 */
@Service
public class AttendanceOvertimeServiceImpl
        extends ServiceImpl<AttendanceOvertimeMapper, AttendanceOvertime>
        implements AttendanceOvertimeService {

    private final DocumentLifecycle lifecycle;

    private final AttendanceOvertimeDocumentAdapter adapter;

    public AttendanceOvertimeServiceImpl(DocumentLifecycle lifecycle,
                                         AttendanceOvertimeDocumentAdapter adapter) {
        this.lifecycle = lifecycle;
        this.adapter = adapter;
    }

    @Override
    public IPage<AttendanceOvertimeVo> page(Page<AttendanceOvertimeVo> page, Long applicantId,
                                            AttendanceApplyQueryVo query) {
        lifecycle.validateStatusFilter(query);
        return baseMapper.selectMyOvertimePage(page, applicantId, query);
    }

    @Override
    public AttendanceOvertimeVo detail(Long applicantId, Long id) {
        return lifecycle.detail(adapter, applicantId, id);
    }

    @Override
    public AttendanceOvertimeVo saveDraft(Long applicantId,
                                          AttendanceOvertimeSaveRequest request) {
        requireTimeOrder(request.getStartTime(), request.getEndTime());
        AttendanceOvertime draft = request.toEntity(applicantId);
        draft.setReason(ApplyTexts.trimToNull(request.getReason()));
        return lifecycle.saveDraft(adapter, applicantId, draft);
    }

    @Override
    public AttendanceOvertimeVo updateDraft(Long applicantId,
                                            AttendanceOvertimeUpdateRequest request) {
        requireTimeOrder(request.getStartTime(), request.getEndTime());
        AttendanceOvertime draft = request.toEntity();
        draft.setReason(ApplyTexts.trimToNull(request.getReason()));
        return lifecycle.updateDraft(adapter, applicantId, request.getId(), draft);
    }

    @Override
    public void removeDraft(Long applicantId, Long id) {
        lifecycle.removeDraft(adapter, applicantId, id);
    }

    @Override
    public AttendanceOvertimeVo submit(Long applicantId, Long id, String rawIdempotencyKey) {
        return lifecycle.submit(adapter, applicantId, id, rawIdempotencyKey);
    }

    private static void requireTimeOrder(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException(201, "加班起止时刻不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(201, "结束时刻必须晚于开始时刻");
        }
    }
}
