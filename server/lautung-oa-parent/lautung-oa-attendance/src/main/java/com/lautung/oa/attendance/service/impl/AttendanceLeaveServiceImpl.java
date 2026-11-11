package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.application.ApplyTexts;
import com.lautung.oa.attendance.application.AttendanceLeaveDocumentAdapter;
import com.lautung.oa.attendance.application.DocumentLifecycle;
import com.lautung.oa.attendance.mapper.AttendanceLeaveMapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveTypeMapper;
import com.lautung.oa.attendance.service.AttendanceLeaveService;
import com.lautung.oa.attendance.service.AttendanceLedgerService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceLeave;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveBalanceVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveVo;
import com.lautung.oa.vo.attendance.AttendanceMyLeaveTypeVo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假单薄壳（#282，ADR 0073；#294 收成壳，ADR 0075）：列表/详情/假期类型选项与本
 * 单据的草稿形状校验留在这里；草稿 CAS、提交序列、同键重放与终态回写全部委托
 * {@link DocumentLifecycle}，领域差异在 {@link AttendanceLeaveDocumentAdapter}。
 * 提交 = 服务端定稿天数与小时数（经考勤日历）+ 校验 + 状态 CAS + 起审批，事务边界
 * 由 lifecycle 拥有。
 */
@Service
public class AttendanceLeaveServiceImpl
        extends ServiceImpl<AttendanceLeaveMapper, AttendanceLeave>
        implements AttendanceLeaveService {

    private final AttendanceLeaveTypeMapper leaveTypeMapper;

    private final AttendanceLedgerService ledgerService;

    private final DocumentLifecycle lifecycle;

    private final AttendanceLeaveDocumentAdapter adapter;

    public AttendanceLeaveServiceImpl(AttendanceLeaveTypeMapper leaveTypeMapper,
                                      AttendanceLedgerService ledgerService,
                                      DocumentLifecycle lifecycle,
                                      AttendanceLeaveDocumentAdapter adapter) {
        this.leaveTypeMapper = leaveTypeMapper;
        this.ledgerService = ledgerService;
        this.lifecycle = lifecycle;
        this.adapter = adapter;
    }

    @Override
    public IPage<AttendanceLeaveVo> page(Page<AttendanceLeaveVo> page, Long applicantId,
                                         AttendanceApplyQueryVo query) {
        lifecycle.validateStatusFilter(query);
        return baseMapper.selectMyLeavePage(page, applicantId, query);
    }

    @Override
    public AttendanceLeaveVo detail(Long applicantId, Long id) {
        return lifecycle.detail(adapter, applicantId, id);
    }

    @Override
    public AttendanceLeaveVo saveDraft(Long applicantId, AttendanceLeaveSaveRequest request) {
        requireEnabledType(request.getLeaveTypeId());
        requireDateOrder(request.getStartDate(), request.getEndDate());
        AttendanceLeave draft = request.toEntity(applicantId);
        draft.setReason(ApplyTexts.trimToNull(request.getReason()));
        return lifecycle.saveDraft(adapter, applicantId, draft);
    }

    @Override
    public AttendanceLeaveVo updateDraft(Long applicantId, AttendanceLeaveUpdateRequest request) {
        requireEnabledType(request.getLeaveTypeId());
        requireDateOrder(request.getStartDate(), request.getEndDate());
        AttendanceLeave draft = request.toEntity();
        draft.setReason(ApplyTexts.trimToNull(request.getReason()));
        return lifecycle.updateDraft(adapter, applicantId, request.getId(), draft);
    }

    @Override
    public void removeDraft(Long applicantId, Long id) {
        lifecycle.removeDraft(adapter, applicantId, id);
    }

    @Override
    public AttendanceLeaveVo submit(Long applicantId, Long id, String rawIdempotencyKey) {
        return lifecycle.submit(adapter, applicantId, id, rawIdempotencyKey);
    }

    @Override
    public List<AttendanceMyLeaveTypeVo> leaveTypes(Long applicantId) {
        List<AttendanceLeaveType> types = leaveTypeMapper.selectList(
                new LambdaQueryWrapper<AttendanceLeaveType>()
                        .eq(AttendanceLeaveType::getStatus, AttendanceLeaveType.STATUS_ENABLED)
                        .orderByAsc(AttendanceLeaveType::getSortNo)
                        .orderByAsc(AttendanceLeaveType::getId));
        Map<Long, BigDecimal> available = new HashMap<>();
        for (AttendanceLeaveBalanceVo balance : ledgerService.balances(applicantId)) {
            available.put(balance.getLeaveTypeId(), balance.getAvailableHours());
        }
        return types.stream().map(type -> {
            AttendanceMyLeaveTypeVo vo = new AttendanceMyLeaveTypeVo();
            vo.setId(type.getId());
            vo.setName(type.getName());
            vo.setBalanceRequired(type.getBalanceRequired());
            if (Integer.valueOf(1).equals(type.getBalanceRequired())) {
                vo.setAvailableHours(available.getOrDefault(type.getId(), BigDecimal.ZERO));
            }
            return vo;
        }).toList();
    }

    private AttendanceLeaveType requireEnabledType(Long leaveTypeId) {
        AttendanceLeaveType type = leaveTypeId == null ? null : leaveTypeMapper.selectById(leaveTypeId);
        if (type == null) {
            throw new BusinessException(201, "假期类型不存在");
        }
        if (!Integer.valueOf(AttendanceLeaveType.STATUS_ENABLED).equals(type.getStatus())) {
            throw new BusinessException(201, "假期类型已停用");
        }
        return type;
    }

    private void requireDateOrder(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(201, "开始日期不能晚于结束日期");
        }
    }
}
