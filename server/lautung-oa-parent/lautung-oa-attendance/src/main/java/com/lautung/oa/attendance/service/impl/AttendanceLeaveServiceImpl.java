package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceLeaveMapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveTypeMapper;
import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.attendance.service.AttendanceLeaveService;
import com.lautung.oa.attendance.service.AttendanceLedgerService;
import com.lautung.oa.attendance.service.AttendanceWorkCalendar;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceBusinessTypes;
import com.lautung.oa.model.attendance.AttendanceLeave;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.business.BusinessApprovalReader;
import com.lautung.oa.process.business.BusinessApprovalStarter;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveBalanceVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveVo;
import com.lautung.oa.vo.attendance.AttendanceMyLeaveTypeVo;
import com.lautung.oa.vo.process.BusinessApprovalStart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请假单实现（#282，ADR 0073/0067）。
 *
 * <p>提交顺序固定：身份与状态 → 类型/事由 → 服务端定稿天数小时（休假日剔除、
 * 补班日照算）→ 区间重叠 → 余额（含在途）→ 状态 CAS → {@link BusinessApprovalStarter}
 * 起审批，全程同一事务；模板映射缺失/未发布/证据失效时 starter 抛出，CAS 随事务回滚，
 * 单据保持草稿。同幂等键重放：单据已审批中且业务键能认出同键时返回同一条审批，
 * 不同键的第二次提交按 201「单据已在审批中」拒绝（状态 CAS 是唯一仲裁）。
 */
@Service
public class AttendanceLeaveServiceImpl
        extends ServiceImpl<AttendanceLeaveMapper, AttendanceLeave>
        implements AttendanceLeaveService {

    private static final Set<Integer> STATUSES = Set.of(
            AttendanceApplyStatus.DRAFT, AttendanceApplyStatus.SUBMITTED,
            AttendanceApplyStatus.APPROVED, AttendanceApplyStatus.REJECTED,
            AttendanceApplyStatus.WITHDRAWN);

    private final AttendanceLeaveTypeMapper leaveTypeMapper;

    private final AttendanceLedgerService ledgerService;

    private final AttendanceUserMapper userMapper;

    private final AttendanceWorkCalendar workCalendar;

    private final BusinessApprovalStarter approvalStarter;

    private final BusinessApprovalReader approvalReader;

    public AttendanceLeaveServiceImpl(AttendanceLeaveTypeMapper leaveTypeMapper,
                                      AttendanceLedgerService ledgerService,
                                      AttendanceUserMapper userMapper,
                                      AttendanceWorkCalendar workCalendar,
                                      BusinessApprovalStarter approvalStarter,
                                      BusinessApprovalReader approvalReader) {
        this.leaveTypeMapper = leaveTypeMapper;
        this.ledgerService = ledgerService;
        this.userMapper = userMapper;
        this.workCalendar = workCalendar;
        this.approvalStarter = approvalStarter;
        this.approvalReader = approvalReader;
    }

    @Override
    public IPage<AttendanceLeaveVo> page(Page<AttendanceLeaveVo> page, Long applicantId,
                                         AttendanceApplyQueryVo query) {
        validateStatusFilter(query);
        return baseMapper.selectMyLeavePage(page, applicantId, query);
    }

    @Override
    public AttendanceLeaveVo detail(Long applicantId, Long id) {
        return detailOrThrow(applicantId, id);
    }

    @Override
    public AttendanceLeaveVo saveDraft(Long applicantId, AttendanceLeaveSaveRequest request) {
        requireEnabledType(request.getLeaveTypeId());
        requireDateOrder(request.getStartDate(), request.getEndDate());
        AttendanceLeave leave = request.toEntity(applicantId);
        leave.setReason(trimToNull(request.getReason()));
        this.save(leave);
        return detailOrThrow(applicantId, leave.getId());
    }

    @Override
    public AttendanceLeaveVo updateDraft(Long applicantId, AttendanceLeaveUpdateRequest request) {
        AttendanceLeave existing = loadOwn(applicantId, request.getId());
        requireEditable(existing, "请假单");
        requireEnabledType(request.getLeaveTypeId());
        requireDateOrder(request.getStartDate(), request.getEndDate());
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, existing.getId())
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.DRAFT,
                        AttendanceApplyStatus.REJECTED, AttendanceApplyStatus.WITHDRAWN)
                .set(AttendanceLeave::getLeaveTypeId, request.getLeaveTypeId())
                .set(AttendanceLeave::getStartDate, request.getStartDate())
                .set(AttendanceLeave::getEndDate, request.getEndDate())
                .set(AttendanceLeave::getReason, trimToNull(request.getReason()))
                .set(AttendanceLeave::getUpdateTime, new Date()));
        requireCas(updated);
        return detailOrThrow(applicantId, existing.getId());
    }

    @Override
    public void removeDraft(Long applicantId, Long id) {
        AttendanceLeave existing = loadOwn(applicantId, id);
        requireEditable(existing, "请假单");
        int removed = baseMapper.delete(new LambdaQueryWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, id)
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.DRAFT,
                        AttendanceApplyStatus.REJECTED, AttendanceApplyStatus.WITHDRAWN));
        if (removed == 0) {
            throw new BusinessException(201, "单据状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public AttendanceLeaveVo submit(Long applicantId, Long id, String rawIdempotencyKey) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        AttendanceLeave leave = loadOwn(applicantId, id);
        if (Integer.valueOf(AttendanceApplyStatus.SUBMITTED).equals(leave.getStatus())) {
            Long started = approvalReader.processIdOfSubmission(
                    AttendanceBusinessTypes.LEAVE, id, applicantId, idempotencyKey);
            if (started != null) {
                return detailOrThrow(applicantId, id);
            }
            throw new BusinessException(201, "单据已在审批中");
        }
        if (!AttendanceApplyStatus.isSubmittable(leave.getStatus())) {
            throw new BusinessException(201, "单据已通过，不能重新提交");
        }

        AttendanceLeaveType type = requireEnabledType(leave.getLeaveTypeId());
        String reason = trimToNull(leave.getReason());
        if (reason == null) {
            throw new BusinessException(201, "请假事由不能为空");
        }
        AttendanceWorkCalendar.WorkdaySpan span =
                workCalendar.calculate(applicantId, leave.getStartDate(), leave.getEndDate());
        requireNoOverlap(applicantId, id, leave.getStartDate(), leave.getEndDate());
        if (Integer.valueOf(1).equals(type.getBalanceRequired())) {
            requireBalance(applicantId, type, span.hours());
        }

        Date now = new Date();
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, id)
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.DRAFT,
                        AttendanceApplyStatus.REJECTED, AttendanceApplyStatus.WITHDRAWN)
                .set(AttendanceLeave::getStatus, AttendanceApplyStatus.SUBMITTED)
                .set(AttendanceLeave::getDays, span.days())
                .set(AttendanceLeave::getHours, span.hours())
                .set(AttendanceLeave::getSubmitTime, now)
                .set(AttendanceLeave::getFinishTime, null)
                .set(AttendanceLeave::getUpdateTime, now));
        requireCas(updated);

        approvalStarter.start(new BusinessApprovalStart(
                applicantOf(applicantId),
                AttendanceBusinessTypes.LEAVE,
                id,
                idempotencyKey,
                leaveSummary(type, leave, span),
                List.of()));
        return detailOrThrow(applicantId, id);
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

    private AttendanceLeaveVo detailOrThrow(Long applicantId, Long id) {
        AttendanceLeaveVo vo = baseMapper.selectMyLeaveDetail(id, applicantId);
        if (vo == null) {
            throw new BusinessException(201, "请假单不存在");
        }
        vo.setApproval(approvalReader.latestSummary(AttendanceBusinessTypes.LEAVE, id));
        return vo;
    }

    private AttendanceLeave loadOwn(Long applicantId, Long id) {
        AttendanceLeave leave = id == null ? null : getById(id);
        if (leave == null || !applicantId.equals(leave.getApplicantId())) {
            throw new BusinessException(201, "请假单不存在");
        }
        return leave;
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

    private void requireDateOrder(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(201, "开始日期不能晚于结束日期");
        }
    }

    private void requireNoOverlap(Long applicantId, Long id,
                                  java.time.LocalDate startDate, java.time.LocalDate endDate) {
        Long overlaps = baseMapper.selectCount(new LambdaQueryWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.SUBMITTED,
                        AttendanceApplyStatus.APPROVED)
                .ne(AttendanceLeave::getId, id)
                .le(AttendanceLeave::getStartDate, endDate)
                .ge(AttendanceLeave::getEndDate, startDate));
        if (overlaps != null && overlaps > 0) {
            throw new BusinessException(201, "与本人已通过或在途的请假日期重叠");
        }
    }

    private void requireBalance(Long applicantId, AttendanceLeaveType type, BigDecimal hours) {
        BigDecimal available = BigDecimal.ZERO;
        for (AttendanceLeaveBalanceVo balance : ledgerService.balances(applicantId)) {
            if (type.getId().equals(balance.getLeaveTypeId())) {
                available = balance.getAvailableHours() == null
                        ? BigDecimal.ZERO : balance.getAvailableHours();
                break;
            }
        }
        if (available.compareTo(hours) < 0) {
            throw new BusinessException(201, "可用余额不足");
        }
    }

    private void requireEditable(AttendanceLeave leave, String label) {
        if (!AttendanceApplyStatus.isEditable(leave.getStatus())) {
            throw new BusinessException(201, label + "当前状态不能修改或删除");
        }
    }

    private void requireCas(int updated) {
        if (updated == 0) {
            throw new BusinessException(201, "单据状态已变化，请刷新后重试");
        }
    }

    private static String requireIdempotencyKey(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null || rawIdempotencyKey.trim().isEmpty()) {
            throw new BusinessException(201, "提交标识不能为空");
        }
        // 长度上限由 BusinessApprovalStarter 校验（W1 端口唯一 owner）；超长在发起时
        // 201 回滚整笔提交，单据保持草稿。
        return rawIdempotencyKey.trim();
    }

    private SysUser applicantOf(Long applicantId) {
        SysUser applicant = userMapper.selectById(applicantId);
        if (applicant == null) {
            throw new BusinessException(201, "申请人不存在");
        }
        if (!StringUtils.hasText(applicant.getName())) {
            applicant.setName(applicant.getUsername());
        }
        return applicant;
    }

    private static String leaveSummary(AttendanceLeaveType type, AttendanceLeave leave,
                                       AttendanceWorkCalendar.WorkdaySpan span) {
        return type.getName() + " " + span.days() + " 天（" + span.hours().toPlainString()
                + " 小时），" + leave.getStartDate() + " 至 " + leave.getEndDate();
    }

    private static void validateStatusFilter(AttendanceApplyQueryVo query) {
        if (query != null && query.getStatus() != null && !STATUSES.contains(query.getStatus())) {
            throw new BusinessException(201, "单据状态不合法");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
