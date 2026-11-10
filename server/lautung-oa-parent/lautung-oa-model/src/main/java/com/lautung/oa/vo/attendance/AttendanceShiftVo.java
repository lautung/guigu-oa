package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceShift;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

/** 今日打卡页展示的班次摘要（#279）：只带页面要用的时刻与宽限，不暴露公共字段。 */
@Data
@Schema(description = "打卡页班次摘要")
public class AttendanceShiftVo {

    @Schema(description = "班次 id")
    private Long id;

    @Schema(description = "班次名称")
    private String name;

    @Schema(description = "上班时刻")
    private LocalTime startTime;

    @Schema(description = "下班时刻")
    private LocalTime endTime;

    @Schema(description = "午休开始时刻，可选")
    private LocalTime lunchStart;

    @Schema(description = "午休结束时刻，可选")
    private LocalTime lunchEnd;

    @Schema(description = "迟到/早退宽限分钟")
    private Integer graceMinutes;

    public static AttendanceShiftVo of(AttendanceShift shift) {
        AttendanceShiftVo vo = new AttendanceShiftVo();
        vo.setId(shift.getId());
        vo.setName(shift.getName());
        vo.setStartTime(shift.getStartTime());
        vo.setEndTime(shift.getEndTime());
        vo.setLunchStart(shift.getLunchStart());
        vo.setLunchEnd(shift.getLunchEnd());
        vo.setGraceMinutes(shift.getGraceMinutes());
        return vo;
    }
}
