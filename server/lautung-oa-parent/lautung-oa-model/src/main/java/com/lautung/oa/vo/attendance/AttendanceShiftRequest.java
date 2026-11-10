package com.lautung.oa.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lautung.oa.model.attendance.AttendanceShift;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * 班次写请求的公共核心（#278，ADR 0034/0073）：Save/Update 两半注解一致的字段与落库填充。
 * 时刻必填性是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），
 * 由各壳在自己的 getter 上声明；长度上限与数值下限只在这里写一次。
 * 时刻格式固定 {@code HH:mm:ss}；下班晚于上班、午休成对且有序在 service 校验。
 */
@Data
public abstract class AttendanceShiftRequest {

    @Size(max = 50, message = "班次名称过长")
    @Schema(description = "班次名称")
    private String name;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(description = "上班时刻")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(description = "下班时刻")
    private LocalTime endTime;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(description = "午休开始时刻，可选")
    private LocalTime lunchStart;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(description = "午休结束时刻，可选")
    private LocalTime lunchEnd;

    @PositiveOrZero(message = "宽限分钟不能为负数")
    @Schema(description = "迟到/早退宽限分钟")
    private Integer graceMinutes;

    @Size(max = 255, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(AttendanceShift shift) {
        shift.setName(name);
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setLunchStart(lunchStart);
        shift.setLunchEnd(lunchEnd);
        shift.setGraceMinutes(graceMinutes);
        shift.setRemark(remark);
    }
}
