package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡事件流水（#279，ADR 0073）：每次打卡一行，append-only。
 *
 * <p>只落事件，不落每日考勤日报；槽位状态读时推导——有补卡行取最新一条补卡行，
 * 否则上班取最早网页行、下班取最晚网页行。网页打卡 {@code operator_user_id} 为空，
 * 补卡行记录后台操作人。数据范围「本人」列是 {@code user_id}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "打卡事件流水")
@TableName("attendance_punch")
public class AttendancePunch extends BaseEntity {

    public static final int SLOT_IN = 1;
    public static final int SLOT_OUT = 2;

    public static final int SOURCE_WEB = 1;
    public static final int SOURCE_REPAIR = 2;

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 id（sys_user.id）")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "打卡日期")
    @TableField("punch_date")
    private LocalDate punchDate;

    @Schema(description = "槽位（1 上班卡 / 2 下班卡）")
    @TableField("slot")
    private Integer slot;

    @Schema(description = "打卡时刻（服务器时间）")
    @TableField("punch_time")
    private LocalDateTime punchTime;

    @Schema(description = "来源（1 网页打卡 / 2 补卡）")
    @TableField("source")
    private Integer source;

    @Schema(description = "补卡操作人；网页打卡为空")
    @TableField("operator_user_id")
    private Long operatorUserId;
}
