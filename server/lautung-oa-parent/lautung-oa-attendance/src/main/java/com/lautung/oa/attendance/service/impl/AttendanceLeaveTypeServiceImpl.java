package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceLeaveTypeMapper;
import com.lautung.oa.attendance.service.AttendanceLeaveTypeService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.vo.attendance.AttendanceLeaveTypeSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveTypeUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 假期类型 CRUD 实现（#280，ADR 0073）。
 *
 * <p>类型被流水或请假单引用后不能删除（201 提示停用），未引用时可作配置修正逻辑删除。
 * 加班入账目标全局唯一：更新时 {@code ne(id, self)} 排除自身，自更新幂等。
 * 更新按完整表单覆盖（含 status），停用即 status=0，历史流水照读。
 */
@Service
public class AttendanceLeaveTypeServiceImpl
        extends ServiceImpl<AttendanceLeaveTypeMapper, AttendanceLeaveType>
        implements AttendanceLeaveTypeService {

    @Override
    public void saveType(AttendanceLeaveTypeSaveRequest request) {
        ensureOvertimeCreditFree(request.getOvertimeCredit(), null);
        this.save(request.toEntity());
    }

    @Override
    public void updateType(AttendanceLeaveTypeUpdateRequest request) {
        AttendanceLeaveType existing = getById(request.getId());
        if (existing == null) {
            throw new BusinessException(201, "假期类型不存在");
        }
        ensureOvertimeCreditFree(request.getOvertimeCredit(), request.getId());
        AttendanceLeaveType type = request.toEntity();
        // 完整表单覆盖：remark 允许清空，显式 set 写空串（updateById 的空值策略会跳过 null）。
        this.update(new LambdaUpdateWrapper<AttendanceLeaveType>()
                .eq(AttendanceLeaveType::getId, type.getId())
                .set(AttendanceLeaveType::getName, type.getName())
                .set(AttendanceLeaveType::getBalanceRequired, type.getBalanceRequired())
                .set(AttendanceLeaveType::getOvertimeCredit, type.getOvertimeCredit())
                .set(AttendanceLeaveType::getStatus, type.getStatus())
                .set(AttendanceLeaveType::getSortNo, type.getSortNo())
                .set(AttendanceLeaveType::getRemark, type.getRemark())
                .set(AttendanceLeaveType::getUpdateTime, new Date()));
    }

    @Override
    public void removeType(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(201, "假期类型不存在");
        }
        if (baseMapper.countLedgerReferences(id) > 0 || baseMapper.countLeaveReferences(id) > 0) {
            throw new BusinessException(201, "假期类型已被引用，只能停用");
        }
        removeById(id);
    }

    private void ensureOvertimeCreditFree(Integer overtimeCredit, Long selfId) {
        if (overtimeCredit == null || overtimeCredit != 1) {
            return;
        }
        Long occupied = baseMapper.selectCount(new LambdaQueryWrapper<AttendanceLeaveType>()
                .eq(AttendanceLeaveType::getOvertimeCredit, 1)
                .ne(selfId != null, AttendanceLeaveType::getId, selfId));
        if (occupied != null && occupied > 0) {
            throw new BusinessException(201, "加班入账目标已存在");
        }
    }
}
