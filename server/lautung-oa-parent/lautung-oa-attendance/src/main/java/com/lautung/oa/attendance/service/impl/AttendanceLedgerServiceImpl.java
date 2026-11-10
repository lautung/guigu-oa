package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceLeaveLedgerMapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveTypeMapper;
import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.attendance.service.AttendanceLedgerService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceLeaveLedger;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceLeaveBalanceVo;
import com.lautung.oa.vo.attendance.AttendanceLedgerGrantRequest;
import com.lautung.oa.vo.attendance.AttendanceLedgerQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLedgerVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 假期余额账本实现（#280，ADR 0073）。
 *
 * <p>发放校验先于写：类型存在且启用、类型需要余额、小时数非 0、每个成员都在
 * 数据范围内（与查询共用 1 = 0 空集零行）。一次多人每人一行 entry_type=1，
 * 写发放操作人；负数行就是调整冲抵，不提供更新/删除入口。
 *
 * <p>流水列表日期按自然日闭区间：结束日期转成次日零点开区间，避免 MySQL/H2
 * 的日期加一天方言差异。余额聚合在 SQL 中按用户 + 类型求和，Java 侧只做
 * 可用余额的加减与 null 兜底。
 */
@Service
public class AttendanceLedgerServiceImpl
        extends ServiceImpl<AttendanceLeaveLedgerMapper, AttendanceLeaveLedger>
        implements AttendanceLedgerService {

    private final AttendanceLeaveTypeMapper leaveTypeMapper;

    private final AttendanceUserMapper userMapper;

    @Autowired
    public AttendanceLedgerServiceImpl(AttendanceLeaveTypeMapper leaveTypeMapper,
                                       AttendanceUserMapper userMapper) {
        this.leaveTypeMapper = leaveTypeMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public int grant(AttendanceLedgerGrantRequest request, Long operatorUserId, DataScope scope) {
        if (request == null || request.getLeaveTypeId() == null) {
            throw new BusinessException(201, "假期类型不能为空");
        }
        if (request.getChangeHours() == null) {
            throw new BusinessException(201, "调整小时数不能为空");
        }
        if (request.getChangeHours().signum() == 0) {
            throw new BusinessException(201, "调整小时数不能为 0");
        }
        AttendanceLeaveType type = leaveTypeMapper.selectById(request.getLeaveTypeId());
        if (type == null) {
            throw new BusinessException(201, "假期类型不存在");
        }
        if (!Integer.valueOf(AttendanceLeaveType.STATUS_ENABLED).equals(type.getStatus())) {
            throw new BusinessException(201, "假期类型已停用");
        }
        if (!Integer.valueOf(1).equals(type.getBalanceRequired())) {
            throw new BusinessException(201, "该假期类型不需要余额");
        }

        Set<Long> userIds = new LinkedHashSet<>();
        if (request.getUserIds() != null) {
            for (Long userId : request.getUserIds()) {
                if (userId != null) {
                    userIds.add(userId);
                }
            }
        }
        if (userIds.isEmpty()) {
            throw new BusinessException(201, "发放成员不能为空");
        }
        for (Long userId : userIds) {
            requireVisibleUser(userId, scope);
        }

        String remark = request.getRemark() == null ? "" : request.getRemark().trim();
        for (Long userId : userIds) {
            AttendanceLeaveLedger ledger = new AttendanceLeaveLedger();
            ledger.setUserId(userId);
            ledger.setLeaveTypeId(type.getId());
            ledger.setEntryType(AttendanceLeaveLedger.ENTRY_GRANT);
            ledger.setChangeHours(request.getChangeHours());
            ledger.setOperatorUserId(operatorUserId);
            ledger.setRemark(remark);
            this.save(ledger);
        }
        return userIds.size();
    }

    @Override
    public IPage<AttendanceLedgerVo> page(Page<AttendanceLedgerVo> page,
                                          AttendanceLedgerQueryVo query,
                                          DataScope scope) {
        validateQuery(query);
        LocalDateTime beginTime = query == null || query.getBeginDate() == null
                ? null : query.getBeginDate().atStartOfDay();
        LocalDateTime endExclusive = query == null || query.getEndDate() == null
                ? null : query.getEndDate().plusDays(1).atStartOfDay();
        return baseMapper.selectLedgerPage(page, query, beginTime, endExclusive, scope);
    }

    @Override
    public List<AttendanceLeaveBalanceVo> balances(Long userId) {
        List<AttendanceLeaveBalanceVo> rows = baseMapper.selectBalances(userId);
        for (AttendanceLeaveBalanceVo row : rows) {
            BigDecimal granted = zeroIfNull(row.getGrantedHours());
            BigDecimal credited = zeroIfNull(row.getCreditedHours());
            BigDecimal deducted = zeroIfNull(row.getDeductedHours());
            BigDecimal pending = zeroIfNull(row.getPendingHours());
            row.setGrantedHours(granted);
            row.setCreditedHours(credited);
            row.setDeductedHours(deducted);
            row.setPendingHours(pending);
            row.setAvailableHours(granted.add(credited).subtract(deducted).subtract(pending));
        }
        return rows;
    }

    private void requireVisibleUser(Long userId, DataScope scope) {
        if (userMapper.selectScopedUsers(null, userId, scope).isEmpty()) {
            // 不存在与范围外同一句：不泄露用户是否存在。
            throw new BusinessException(201, "成员不在数据范围内");
        }
    }

    private void validateQuery(AttendanceLedgerQueryVo query) {
        if (query == null) {
            return;
        }
        if (query.getBeginDate() != null && query.getEndDate() != null
                && query.getBeginDate().isAfter(query.getEndDate())) {
            throw new BusinessException(201, "开始日期不能晚于结束日期");
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
