package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceHolidayMapper;
import com.lautung.oa.attendance.service.AttendanceHolidayService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.vo.attendance.AttendanceHolidaySaveRequest;
import com.lautung.oa.vo.attendance.AttendanceHolidayUpdateRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 全局节假日维护实现（#278，ADR 0073）：类型只收 1/2；日期唯一先查后插，
 * 并发漏网由 {@code uk_attendance_holiday_date} 兜底并翻译成同一文案；
 * 行删除硬删，同日期可重建。
 */
@Service
public class AttendanceHolidayServiceImpl extends ServiceImpl<AttendanceHolidayMapper, AttendanceHoliday>
        implements AttendanceHolidayService {

    @Override
    public void saveHoliday(AttendanceHolidaySaveRequest request) {
        ensureDayTypeValid(request.getDayType());
        ensureDateAvailable(request.getHolidayDate(), null);
        try {
            this.save(request.toEntity());
        } catch (DuplicateKeyException duplicate) {
            throw duplicateDate();
        }
    }

    @Override
    public void updateHoliday(AttendanceHolidayUpdateRequest request) {
        AttendanceHoliday existing = getById(request.getId());
        if (existing == null) {
            throw new BusinessException(201, "节假日不存在");
        }
        if (request.getDayType() != null) {
            ensureDayTypeValid(request.getDayType());
        }
        if (request.getHolidayDate() != null) {
            ensureDateAvailable(request.getHolidayDate(), request.getId());
        }
        try {
            updateById(request.toEntity());
        } catch (DuplicateKeyException duplicate) {
            throw duplicateDate();
        }
    }

    @Override
    public void removeHoliday(Long id) {
        // 硬删：配置修正不走软删，同日期可重建（ADR 0073）。
        this.baseMapper.deleteById(id);
    }

    private void ensureDayTypeValid(Integer dayType) {
        if (dayType == null || (dayType != AttendanceHoliday.DAY_TYPE_HOLIDAY
                && dayType != AttendanceHoliday.DAY_TYPE_WORKDAY)) {
            throw new BusinessException(201, "节假日类型不合法");
        }
    }

    private void ensureDateAvailable(LocalDate date, Long excludeId) {
        Long count = count(new LambdaQueryWrapper<AttendanceHoliday>()
                .eq(AttendanceHoliday::getHolidayDate, date)
                .ne(excludeId != null, AttendanceHoliday::getId, excludeId));
        if (count != null && count > 0) {
            throw duplicateDate();
        }
    }

    private BusinessException duplicateDate() {
        return new BusinessException(201, "该日期已存在节假日");
    }
}
