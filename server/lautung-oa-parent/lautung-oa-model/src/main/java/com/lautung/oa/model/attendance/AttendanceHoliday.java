package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 全局节假日日历（#278，ADR 0073）：日期唯一，对全部考勤组生效。
 *
 * <p>day_type：1 休假日 / 2 补班日。工作日判定公式：
 * (星期 ∈ 组工作日 且 该日非休假日) 或 该日是补班日。行删除为硬删（配置修正，
 * 操作日志留痕），维护入口在「考勤组与班次」页的节假日区块。
 */
@Data
@Schema(description = "全局节假日")
@TableName("attendance_holiday")
public class AttendanceHoliday implements Serializable {

    public static final int DAY_TYPE_HOLIDAY = 1;
    public static final int DAY_TYPE_WORKDAY = 2;

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "节假日日期，全局唯一")
    @TableField("holiday_date")
    private LocalDate holidayDate;

    @Schema(description = "名称")
    @TableField("name")
    private String name;

    @Schema(description = "类型（1 休假日 / 2 补班日）")
    @TableField("day_type")
    private Integer dayType;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
