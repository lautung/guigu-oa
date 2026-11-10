package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceLeaveTypeMapper;
import com.lautung.oa.attendance.mapper.AttendanceOvertimeMapper;
import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.attendance.service.AttendanceOvertimeService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceBusinessTypes;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.model.attendance.AttendanceOvertime;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.approval.ApproverNames;
import com.lautung.oa.process.business.BusinessApprovalReader;
import com.lautung.oa.process.business.BusinessApprovalStarter;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceOvertimeSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeVo;
import com.lautung.oa.vo.process.BusinessApprovalStart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 加班单实现（#282，ADR 0073/0067）。
 *
 * <p>时长服务端算：结束必须晚于开始且同一天内（时刻同一日期，跨天由客户端拆两天）。
 * 允许休息日加班，不校验考勤组、不与打卡记录交叉校验、不做重叠校验。
 * 提交校验调休入账目标存在（加班单通过后必须有人接这笔入账），随后状态 CAS +
 * BusinessApprovalStarter 同一事务；模板映射缺失/未发布/证据失效时单据保持草稿。
 */
@Service
public class AttendanceOvertimeServiceImpl
        extends ServiceImpl<AttendanceOvertimeMapper, AttendanceOvertime>
        implements AttendanceOvertimeService {

    private static final Set<Integer> STATUSES = Set.of(
            AttendanceApplyStatus.DRAFT, AttendanceApplyStatus.SUBMITTED,
            AttendanceApplyStatus.APPROVED, AttendanceApplyStatus.REJECTED,
            AttendanceApplyStatus.WITHDRAWN);

    private final AttendanceLeaveTypeMapper leaveTypeMapper;

    private final AttendanceUserMapper userMapper;

    private final BusinessApprovalStarter approvalStarter;

    private final BusinessApprovalReader approvalReader;

    public AttendanceOvertimeServiceImpl(AttendanceLeaveTypeMapper leaveTypeMapper,
                                         AttendanceUserMapper userMapper,
                                         BusinessApprovalStarter approvalStarter,
                                         BusinessApprovalReader approvalReader) {
        this.leaveTypeMapper = leaveTypeMapper;
        this.userMapper = userMapper;
        this.approvalStarter = approvalStarter;
        this.approvalReader = approvalReader;
    }

    @Override
    public IPage<AttendanceOvertimeVo> page(Page<AttendanceOvertimeVo> page, Long applicantId,
                                            AttendanceApplyQueryVo query) {
        validateStatusFilter(query);
        return baseMapper.selectMyOvertimePage(page, applicantId, query);
    }

    @Override
    public AttendanceOvertimeVo detail(Long applicantId, Long id) {
        return detailOrThrow(applicantId, id);
    }

    @Override
    public AttendanceOvertimeVo saveDraft(Long applicantId,
                                          AttendanceOvertimeSaveRequest request) {
        requireTimeOrder(request.getStartTime(), request.getEndTime());
        AttendanceOvertime overtime = request.toEntity(applicantId);
        overtime.setReason(trimToNull(request.getReason()));
        this.save(overtime);
        return detailOrThrow(applicantId, overtime.getId());
    }

    @Override
    public AttendanceOvertimeVo updateDraft(Long applicantId,
                                            AttendanceOvertimeUpdateRequest request) {
        AttendanceOvertime existing = loadOwn(applicantId, request.getId());
        requireEditable(existing);
        requireTimeOrder(request.getStartTime(), request.getEndTime());
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, existing.getId())
                .eq(AttendanceOvertime::getApplicantId, applicantId)
                .in(AttendanceOvertime::getStatus, AttendanceApplyStatus.DRAFT,
                        AttendanceApplyStatus.REJECTED, AttendanceApplyStatus.WITHDRAWN)
                .set(AttendanceOvertime::getOvertimeDate, request.getOvertimeDate())
                .set(AttendanceOvertime::getStartTime, request.getStartTime())
                .set(AttendanceOvertime::getEndTime, request.getEndTime())
                .set(AttendanceOvertime::getReason, trimToNull(request.getReason()))
                .set(AttendanceOvertime::getUpdateTime, new Date()));
        requireCas(updated);
        return detailOrThrow(applicantId, existing.getId());
    }

    @Override
    public void removeDraft(Long applicantId, Long id) {
        AttendanceOvertime existing = loadOwn(applicantId, id);
        requireEditable(existing);
        int removed = baseMapper.delete(new LambdaQueryWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, id)
                .eq(AttendanceOvertime::getApplicantId, applicantId)
                .in(AttendanceOvertime::getStatus, AttendanceApplyStatus.DRAFT,
                        AttendanceApplyStatus.REJECTED, AttendanceApplyStatus.WITHDRAWN));
        if (removed == 0) {
            throw new BusinessException(201, "单据状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public AttendanceOvertimeVo submit(Long applicantId, Long id, String rawIdempotencyKey) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        AttendanceOvertime overtime = loadOwn(applicantId, id);
        if (Integer.valueOf(AttendanceApplyStatus.SUBMITTED).equals(overtime.getStatus())) {
            Long started = approvalReader.processIdOfSubmission(
                    AttendanceBusinessTypes.OVERTIME, id, applicantId, idempotencyKey);
            if (started != null) {
                return detailOrThrow(applicantId, id);
            }
            throw new BusinessException(201, "单据已在审批中");
        }
        if (!AttendanceApplyStatus.isSubmittable(overtime.getStatus())) {
            throw new BusinessException(201, "单据已通过，不能重新提交");
        }

        String reason = trimToNull(overtime.getReason());
        if (reason == null) {
            throw new BusinessException(201, "加班事由不能为空");
        }
        requireTimeOrder(overtime.getStartTime(), overtime.getEndTime());
        requireOvertimeCreditType();
        BigDecimal hours = hoursOf(overtime.getStartTime(), overtime.getEndTime());

        Date now = new Date();
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, id)
                .eq(AttendanceOvertime::getApplicantId, applicantId)
                .in(AttendanceOvertime::getStatus, AttendanceApplyStatus.DRAFT,
                        AttendanceApplyStatus.REJECTED, AttendanceApplyStatus.WITHDRAWN)
                .set(AttendanceOvertime::getStatus, AttendanceApplyStatus.SUBMITTED)
                .set(AttendanceOvertime::getHours, hours)
                .set(AttendanceOvertime::getSubmitTime, now)
                .set(AttendanceOvertime::getFinishTime, null)
                .set(AttendanceOvertime::getUpdateTime, now));
        requireCas(updated);

        approvalStarter.start(new BusinessApprovalStart(
                applicantOf(applicantId),
                AttendanceBusinessTypes.OVERTIME,
                id,
                idempotencyKey,
                overtimeSummary(overtime, hours),
                List.of()));
        return detailOrThrow(applicantId, id);
    }

    private AttendanceOvertimeVo detailOrThrow(Long applicantId, Long id) {
        AttendanceOvertimeVo vo = baseMapper.selectMyOvertimeDetail(id, applicantId);
        if (vo == null) {
            throw new BusinessException(201, "加班单不存在");
        }
        vo.setApproval(approvalReader.latestSummary(AttendanceBusinessTypes.OVERTIME, id));
        return vo;
    }

    private AttendanceOvertime loadOwn(Long applicantId, Long id) {
        AttendanceOvertime overtime = id == null ? null : getById(id);
        if (overtime == null || !applicantId.equals(overtime.getApplicantId())) {
            throw new BusinessException(201, "加班单不存在");
        }
        return overtime;
    }

    /**
     * 加班入账目标（ADR 0073：仅调休开启，全局唯一）。提交时校验是为了避免
     * 「审批通过却写不了入账」——回调失败会把通过动作整体回滚。
     */
    private AttendanceLeaveType requireOvertimeCreditType() {
        List<AttendanceLeaveType> types = leaveTypeMapper.selectList(
                new LambdaQueryWrapper<AttendanceLeaveType>()
                        .eq(AttendanceLeaveType::getOvertimeCredit, 1)
                        .eq(AttendanceLeaveType::getStatus, AttendanceLeaveType.STATUS_ENABLED)
                        .orderByAsc(AttendanceLeaveType::getId));
        if (types.isEmpty()) {
            throw new BusinessException(201, "未配置加班入账的调休类型");
        }
        return types.get(0);
    }

    private static void requireTimeOrder(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException(201, "加班起止时刻不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(201, "结束时刻必须晚于开始时刻");
        }
    }

    private static BigDecimal hoursOf(LocalTime startTime, LocalTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private void requireEditable(AttendanceOvertime overtime) {
        if (!AttendanceApplyStatus.isEditable(overtime.getStatus())) {
            throw new BusinessException(201, "加班单当前状态不能修改或删除");
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
        String key = rawIdempotencyKey.trim();
        if (key.length() > ApproverNames.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(201, "提交标识过长");
        }
        return key;
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

    private static String overtimeSummary(AttendanceOvertime overtime, BigDecimal hours) {
        return "加班 " + hours.toPlainString() + " 小时：" + overtime.getOvertimeDate()
                + " " + overtime.getStartTime() + "-" + overtime.getEndTime();
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
