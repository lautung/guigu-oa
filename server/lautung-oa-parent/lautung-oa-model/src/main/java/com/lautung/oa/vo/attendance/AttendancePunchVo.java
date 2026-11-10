package com.lautung.oa.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 后台打卡记录行（#279）：join 姓名/部门/补卡操作人，数据范围落在 sys_user 当前部门。 */
@Data
@Schema(description = "打卡记录行")
public class AttendancePunchVo {

    @Schema(description = "流水行 id")
    private Long id;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "用户当前部门 id")
    private Long deptId;

    @Schema(description = "用户当前部门名称")
    private String deptName;

    @Schema(description = "打卡日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate punchDate;

    @Schema(description = "槽位（1 上班卡 / 2 下班卡）")
    private Integer slot;

    @Schema(description = "打卡时刻")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime punchTime;

    @Schema(description = "来源（1 网页打卡 / 2 补卡）")
    private Integer source;

    @Schema(description = "补卡操作人姓名；网页打卡为空")
    private String operatorName;
}
